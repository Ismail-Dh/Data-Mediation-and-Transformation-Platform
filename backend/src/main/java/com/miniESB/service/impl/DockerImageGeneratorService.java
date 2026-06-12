package com.miniESB.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.*;
import com.miniESB.domain.enums.ImageStatus;
import com.miniESB.domain.enums.PipelineStatus;
import com.miniESB.dto.docker.DockerImageBuildResponse;
import com.miniESB.dto.docker.DockerImageResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)

@RequiredArgsConstructor
public class DockerImageGeneratorService {

    private final PipelineRepository       pipelineRepository;
    private final MappingRuleRepository    mappingRuleRepository;
    private final PipelineFieldRepository  pipelineFieldRepository;
    private final DockerImageRepository    dockerImageRepository;
    private final BuildLogEntryRepository  buildLogEntryRepository;   // ← injecté pour tâche 5.4
    private final ObjectMapper             objectMapper;

    // ══════════════════════════════════════════════════════════════════════════
    //  5.4 — SSE streaming build
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Lance le build Docker dans un thread dédié (@Async) et streame chaque
     * ligne de log via {@code emitter}.
     *
     * <p>Événements SSE émis :
     * <ul>
     *   <li>Messages sans nom (data: &lt;ligne brute&gt;) — pendant le build</li>
     *   <li>{@code BUILD_COMPLETE} — {@code {type, imageId, size, duration}}</li>
     *   <li>{@code BUILD_FAILED}   — {@code {type, errorMessage}}</li>
     * </ul>
     */
    @Async("buildSseExecutor")
    public void generateImageWithSse(Long pipelineId, SseEmitter emitter) {

        LocalDateTime startTime = LocalDateTime.now();
        StringBuilder fullLog   = new StringBuilder();

        // ── 1. Charger le pipeline ────────────────────────────────────────────
        Pipeline pipeline;
        try {
            pipeline = pipelineRepository.findById(pipelineId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Pipeline not found with id=" + pipelineId));
        } catch (ResourceNotFoundException e) {
            sendSseErrorAndComplete(emitter, e.getMessage());
            return;
        }

        if (pipeline.getStatus() != PipelineStatus.VALIDATED) {
            sendSseErrorAndComplete(emitter,
                    "Pipeline must be VALIDATED to generate an image — current status: "
                            + pipeline.getStatus());
            return;
        }

        // ── 2. Créer / mettre à jour l'entrée DockerImage ────────────────────
        String imageName = "pipeline-" + pipelineId;
        String tag       = pipeline.getVersion() != null ? pipeline.getVersion() : "latest";

        DockerImage dockerImage = dockerImageRepository
                .findByPipelineId(pipelineId)
                .orElse(DockerImage.builder().pipeline(pipeline).build());

        dockerImage.setImageName(imageName);
        dockerImage.setTag(tag);
        dockerImage.setStatus(ImageStatus.BUILDING);
        dockerImage.setBuiltAt(null);
        dockerImageRepository.save(dockerImage);

        // ── 3. Build + streaming ──────────────────────────────────────────────
        Path buildDir = null;
        try {
            buildDir = Files.createTempDirectory("pipeline-build-sse-" + pipelineId);

            generateRulesJson(pipelineId, buildDir);
            generateDockerfile(buildDir);

            String fullImageName = imageName + ":" + tag;
            Process process = new ProcessBuilder(
                    "docker", "build", "-t", fullImageName, buildDir.toString())
                    .redirectErrorStream(true)
                    .start();

            // Streamer ligne par ligne vers le frontend
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    fullLog.append(line).append("\n");
                    emitter.send(line, MediaType.TEXT_PLAIN);  // event sans nom → onmessage
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException(
                        "docker build failed with exit code: " + exitCode);
            }

            // ── 4. Succès ─────────────────────────────────────────────────────
            Long sizeBytes = getImageSize(fullImageName);
            LocalDateTime endTime = LocalDateTime.now();

            dockerImage.setStatus(ImageStatus.SUCCESS);
            dockerImage.setBuiltAt(endTime);
            dockerImage.setSizeBytes(sizeBytes);
            dockerImageRepository.save(dockerImage);

            saveBuildLogEntry(pipeline, dockerImage, fullLog.toString(),
                    ImageStatus.SUCCESS, startTime, endTime);

            long durationSec = Duration.between(startTime, endTime).toSeconds();
            Map<String, Object> completeEvent = new LinkedHashMap<>();
            completeEvent.put("type",     "BUILD_COMPLETE");
            completeEvent.put("imageId",  dockerImage.getId());
            completeEvent.put("size",     sizeBytes != null ? sizeBytes : 0L);
            completeEvent.put("duration", durationSec);

            emitter.send(SseEmitter.event()
                    .name("BUILD_COMPLETE")
                    .data(objectMapper.writeValueAsString(completeEvent)));
            emitter.complete();

            log.info("SSE build complete for pipeline={} in {}s", pipelineId, durationSec);

        } catch (Exception e) {
            log.error("SSE build FAILED for pipeline={}: {}", pipelineId, e.getMessage());

            dockerImage.setStatus(ImageStatus.FAILED);
            dockerImageRepository.save(dockerImage);

            saveBuildLogEntry(pipeline, dockerImage, fullLog.toString(),
                    ImageStatus.FAILED, startTime, LocalDateTime.now());

            sendSseErrorAndComplete(emitter, e.getMessage());

        } finally {
            if (buildDir != null) deleteDirectory(buildDir);
        }
    }

    // ── Persistance BuildLogEntry ─────────────────────────────────────────────

    private void saveBuildLogEntry(Pipeline pipeline, DockerImage dockerImage,
                                   String fullLog, ImageStatus status,
                                   LocalDateTime startTime, LocalDateTime endTime) {
        try {
            BuildLogEntry entry = BuildLogEntry.builder()
                    .pipelineId(pipeline.getId())
                    .version(pipeline.getVersion())
                    .fullLog(fullLog)
                    .status(status)
                    .startTime(startTime)
                    .endTime(endTime)
                    .dockerImage(dockerImage)
                    .build();
            buildLogEntryRepository.save(entry);
        } catch (Exception ex) {
            log.warn("Could not persist BuildLogEntry for pipeline={}: {}",
                    pipeline.getId(), ex.getMessage());
        }
    }

    // ── Helper SSE error ──────────────────────────────────────────────────────

    private void sendSseErrorAndComplete(SseEmitter emitter, String message) {
        try {
            Map<String, String> payload = Map.of(
                    "type",         "BUILD_FAILED",
                    "errorMessage", message != null ? message : "Unknown error"
            );
            emitter.send(SseEmitter.event()
                    .name("BUILD_FAILED")
                    .data(objectMapper.writeValueAsString(payload)));
            emitter.complete();
        } catch (IOException ignored) {
            // Client déjà déconnecté
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Méthodes existantes (inchangées)
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public DockerImageBuildResponse generateImage(Long pipelineId) throws Exception {

        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));

        if (pipeline.getStatus() != PipelineStatus.VALIDATED) {
            throw new IllegalStateException(
                    "Pipeline must be VALIDATED to generate an image — current status: "
                            + pipeline.getStatus());
        }

        String imageName = "pipeline-" + pipelineId;
        String tag       = "latest";

        DockerImage dockerImage = dockerImageRepository
                .findByPipelineId(pipelineId)
                .orElse(DockerImage.builder().pipeline(pipeline).build());

        dockerImage.setImageName(imageName);
        dockerImage.setTag(tag);
        dockerImage.setStatus(ImageStatus.PENDING);
        dockerImage.setBuiltAt(null);
        dockerImageRepository.save(dockerImage);

        Path buildDir = Files.createTempDirectory("pipeline-build-" + pipelineId);
        log.info("Build directory created: {}", buildDir);

        try {
            generateRulesJson(pipelineId, buildDir);
            generateDockerfile(buildDir);

            dockerImage.setStatus(ImageStatus.BUILDING);
            dockerImageRepository.save(dockerImage);

            buildDockerImage(imageName, tag, buildDir);

            Long sizeBytes = getImageSize(imageName + ":" + tag);

            dockerImage.setStatus(ImageStatus.SUCCESS);
            dockerImage.setBuiltAt(LocalDateTime.now());
            dockerImage.setSizeBytes(sizeBytes);
            dockerImageRepository.save(dockerImage);

            log.info("Docker image generated successfully: {}:{}", imageName, tag);

            return new DockerImageBuildResponse(
                    dockerImage.getId(), imageName, tag, "SUCCESS", "Image built successfully");

        } catch (Exception e) {
            dockerImage.setStatus(ImageStatus.FAILED);
            dockerImageRepository.save(dockerImage);
            log.error("Docker image generation FAILED for pipeline={}: {}",
                    pipelineId, e.getMessage());
            throw e;

        } finally {
            deleteDirectory(buildDir);
        }
    }

    // ── Generate rules.json ───────────────────────────────────────────────────

    private void generateRulesJson(Long pipelineId, Path buildDir) throws Exception {
        List<MappingRule>    mappingRules     = mappingRuleRepository.findByPipelineIdAndActiveTrue(pipelineId);
        List<PipelineField>  validationFields = pipelineFieldRepository.findAllByPipelineId(pipelineId);

        List<Map<String, Object>> mappingList = mappingRules.stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("sourceField", r.getSourceField());
                    m.put("targetField", r.getTargetField());
                    m.put("mappingType", r.getMappingType().name());
                    m.put("expression",  r.getExpression());
                    return m;
                }).toList();

        List<Map<String, Object>> validationList = validationFields.stream()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("fieldPath", f.getFieldPath());
                    m.put("fieldType", f.getFieldType().name());
                    m.put("required",  f.isRequired());
                    m.put("nullable",  f.isNullable());
                    return m;
                }).toList();

        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("pipelineId",       pipelineId);
        rules.put("mappingRules",     mappingList);
        rules.put("validationFields", validationList);

        File rulesFile = buildDir.resolve("rules.json").toFile();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(rulesFile, rules);
        log.info("rules.json generated: {}", rulesFile.getAbsolutePath());
    }

    // ── Generate Dockerfile ───────────────────────────────────────────────────

    private void generateDockerfile(Path buildDir) throws Exception {
        String dockerfileContent = """
                FROM mini-esb-backend:latest
                COPY rules.json /app/rules.json
                ENV ENGINE_MODE=true
                ENV RULES_FILE=/app/rules.json
                EXPOSE 8080
                ENTRYPOINT ["java", "-jar", "app.jar", \
                "--engine.mode=true", \
                "--engine.rules-file=/app/rules.json", \
                "--spring.profiles.active=engine"]
                """;
        Files.writeString(buildDir.resolve("Dockerfile"), dockerfileContent);
        log.info("Dockerfile generated");
    }

    // ── Docker build (synchrone, méthode historique) ──────────────────────────

    private void buildDockerImage(String imageName, String tag, Path buildDir) throws Exception {
        String fullName = imageName + ":" + tag;
        List<String> command = List.of("docker", "build", "-t", fullName, buildDir.toString());
        log.info("Running: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[docker build] {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("docker build failed with exit code: " + exitCode);
        }
        log.info("Docker image built successfully: {}", fullName);
    }

    // ── Get image size ────────────────────────────────────────────────────────

    private Long getImageSize(String imageFullName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "inspect", "--format={{.Size}}", imageFullName);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    return Long.parseLong(line.trim());
                }
            }
        } catch (Exception e) {
            log.warn("Could not retrieve image size: {}", e.getMessage());
        }
        return null;
    }

    // ── Export image ──────────────────────────────────────────────────────────

    public byte[] exportImage(Long pipelineId) throws Exception {
        DockerImage dockerImage = dockerImageRepository.findByPipelineId(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No image found for pipeline id=" + pipelineId));

        if (dockerImage.getStatus() != ImageStatus.SUCCESS) {
            throw new IllegalStateException(
                    "Image is not ready — current status: " + dockerImage.getStatus());
        }

        String fullName = dockerImage.getImageName() + ":" + dockerImage.getTag();
        ProcessBuilder pb = new ProcessBuilder("docker", "save", fullName);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        byte[] imageBytes = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("docker save failed with exit code: " + exitCode);
        }

        log.info("Image exported: {} ({} bytes)", fullName, imageBytes.length);
        return imageBytes;
    }

    // ── Get image info ────────────────────────────────────────────────────────

    public DockerImageResponse getImageInfo(Long pipelineId) {
        DockerImage image = dockerImageRepository.findByPipelineId(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No image found for pipeline id=" + pipelineId));
        return toResponse(image);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void deleteDirectory(Path dir) {
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (Exception e) {
            log.warn("Could not delete temp directory: {}", e.getMessage());
        }
    }

    private DockerImageResponse toResponse(DockerImage image) {
        return new DockerImageResponse(
                image.getId(),
                image.getImageName(),
                image.getTag(),
                image.getStatus(),
                image.getSizeBytes(),
                image.getBuiltAt(),
                image.getPipeline().getId()
        );
    }
}