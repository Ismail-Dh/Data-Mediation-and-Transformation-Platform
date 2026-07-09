package com.miniESB.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.*;
import com.miniESB.domain.enums.ImageStatus;
import com.miniESB.domain.enums.PipelineStatus;
import com.miniESB.dto.docker.BuildLogEntryResponse;
import com.miniESB.dto.docker.DockerImageBuildResponse;
import com.miniESB.dto.docker.DockerImageResponse;
import com.miniESB.exception.DockerBuildException;
import com.miniESB.exception.DockerDaemonException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.miniESB.service.ImageVersionService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.net.ConnectException;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import com.miniESB.domain.entity.ResponseMappingRule;
import com.miniESB.repository.ResponseMappingRuleRepository;

@Slf4j
@Service
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class DockerImageGeneratorService {

    private final PipelineRepository       pipelineRepository;
    private final MappingRuleRepository    mappingRuleRepository;
    private final PipelineFieldRepository  pipelineFieldRepository;
    private final DockerImageRepository    dockerImageRepository;
    private final BuildLogEntryRepository  buildLogEntryRepository;
    private final ObjectMapper             objectMapper;
    // Ajouter dans les champs injectés
    private final ImageVersionService      imageVersionService;
    private final ResponseMappingRuleRepository responseMappingRuleRepository;


    // ══════════════════════════════════════════════════════════════════════════
    //  SSE streaming build  (tâche 5.4 + error handling renforcé)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Lance le build Docker dans un thread dédié (@Async) et streame chaque
     * ligne de log via {@code emitter}.
     *
     * <p>Événements SSE émis :
     * <ul>
     *   <li>Messages sans nom (data: &lt;ligne brute&gt;) — pendant le build</li>
     *   <li>{@code BUILD_COMPLETE} — {@code {type, imageId, size, duration}}</li>
     *   <li>{@code BUILD_FAILED}   — {@code {type, errorType, errorMessage, exitCode?, buildLog?}}</li>
     * </ul>
     *
     * <p><strong>Garantie pipeline</strong> : quelle que soit l'erreur (daemon injoignable ou
     * build échoué), le statut du pipeline reste {@code VALIDATED}. Seul le {@link DockerImage}
     * passe en {@code FAILED}.
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
            sendSseErrorAndComplete(emitter, "PIPELINE_NOT_FOUND", e.getMessage(), null, null);
            return;
        }

        if (pipeline.getStatus() != PipelineStatus.VALIDATED) {
            sendSseErrorAndComplete(emitter, "PIPELINE_NOT_VALIDATED",
                    "Pipeline must be VALIDATED to generate an image — current status: "
                            + pipeline.getStatus(), null, null);
            return;
        }

        // ── 2. Créer / mettre à jour l'entrée DockerImage ────────────────────
        String imageName = "pipeline-" + pipelineId;
        String currentPipelineVersion = pipeline.getVersion() != null
                ? pipeline.getVersion()
                : "1.0";

        DockerImage dockerImage = dockerImageRepository
                .findByPipelineId(pipelineId)
                .orElse(DockerImage.builder().pipeline(pipeline).build());

        // Calcul du prochain tag sémantique
        String nextTag = imageVersionService.computeNextTag(dockerImage, pipeline);

        dockerImage.setImageName(imageName);
        dockerImage.setTag(nextTag);
        dockerImage.setStatus(ImageStatus.BUILDING);
        dockerImage.setBuiltAt(null);
        dockerImageRepository.save(dockerImage);

        // ── 3. Build + streaming ──────────────────────────────────────────────
        Path buildDir = null;
        try {
            buildDir = Files.createTempDirectory("pipeline-build-sse-" + pipelineId);

            generateRulesJson(pipelineId, buildDir);
            generateDockerfile(buildDir);

            String fullImageName = imageName + ":" + nextTag;

            // ── 3a. Vérifier que le daemon Docker répond ──────────────────────
            checkDockerDaemon();

            // ── 3b. Lancer le build ───────────────────────────────────────────
            Process process;
            try {
                process = new ProcessBuilder(
                        "docker", "build", "-t", fullImageName, buildDir.toString())
                        .redirectErrorStream(true)
                        .start();
            } catch (IOException e) {
                // Le daemon est joignable mais start() a quand même échoué
                throw new DockerDaemonException(
                        "Failed to start docker build process", e);
            }

            // Streamer ligne par ligne vers le frontend
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    fullLog.append(line).append("\n");
                    emitter.send(line, MediaType.TEXT_PLAIN);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // Build échoué — pipeline reste VALIDATED
                dockerImage.setStatus(ImageStatus.FAILED);
                dockerImageRepository.save(dockerImage);
                saveBuildLogEntry(pipeline, dockerImage, fullLog.toString(),
                        ImageStatus.FAILED, startTime, LocalDateTime.now());

                sendSseErrorAndComplete(emitter, "BUILD_ERROR",
                        "docker build failed with exit code: " + exitCode,
                        exitCode, fullLog.toString());
                return;
            }

            // ── 4. Succès ─────────────────────────────────────────────────────
            Long sizeBytes = getImageSize(imageName + ":" + nextTag);
            LocalDateTime endTime = LocalDateTime.now();

            // Appliquer la version sémantique sur l'entité
            imageVersionService.applyNextVersion(dockerImage, nextTag, currentPipelineVersion);

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
            completeEvent.put("tag",      nextTag);               // ← tag sémantique
            completeEvent.put("size",     sizeBytes != null ? sizeBytes : 0L);
            completeEvent.put("duration", durationSec);

            emitter.send(SseEmitter.event()
                    .name("BUILD_COMPLETE")
                    .data(objectMapper.writeValueAsString(completeEvent)));
            emitter.complete();

            log.info("SSE build complete for pipeline={} in {}s", pipelineId, durationSec);

        } catch (DockerDaemonException e) {
            // Daemon injoignable — pipeline reste VALIDATED, image → FAILED
            log.error("Docker daemon unreachable for pipeline={}: {}", pipelineId, e.getMessage());
            dockerImage.setStatus(ImageStatus.FAILED);
            dockerImageRepository.save(dockerImage);
            saveBuildLogEntry(pipeline, dockerImage, fullLog.toString(),
                    ImageStatus.FAILED, startTime, LocalDateTime.now());

            sendSseErrorAndComplete(emitter, "DAEMON_UNREACHABLE",
                    e.getMessage() + (e.getTechnicalDetail() != null
                            ? " — " + e.getTechnicalDetail() : ""),
                    null, null);

        } catch (DockerBuildException e) {
            // build échoué via chemin synchrone (ne devrait pas arriver ici, mais défensif)
            log.error("Docker build failed for pipeline={} (exit={}): {}",
                    pipelineId, e.getExitCode(), e.getMessage());
            dockerImage.setStatus(ImageStatus.FAILED);
            dockerImageRepository.save(dockerImage);
            saveBuildLogEntry(pipeline, dockerImage,
                    e.getBuildLog() != null ? e.getBuildLog() : fullLog.toString(),
                    ImageStatus.FAILED, startTime, LocalDateTime.now());

            sendSseErrorAndComplete(emitter, "BUILD_ERROR",
                    e.getMessage(), e.getExitCode(), e.getBuildLog());

        } catch (Exception e) {
            log.error("SSE build FAILED for pipeline={}: {}", pipelineId, e.getMessage());
            dockerImage.setStatus(ImageStatus.FAILED);
            dockerImageRepository.save(dockerImage);
            saveBuildLogEntry(pipeline, dockerImage, fullLog.toString(),
                    ImageStatus.FAILED, startTime, LocalDateTime.now());

            sendSseErrorAndComplete(emitter, "BUILD_FAILED", e.getMessage(), null, null);

        } finally {
            if (buildDir != null) deleteDirectory(buildDir);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Build synchrone (endpoint POST /generate) + error handling renforcé
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Build synchrone (sans SSE).
     *
     * <p>Lance les deux exceptions typées :
     * <ul>
     *   <li>{@link DockerDaemonException} → HTTP 503, pipeline reste {@code VALIDATED}</li>
     *   <li>{@link DockerBuildException}  → HTTP 422, pipeline reste {@code VALIDATED}</li>
     * </ul>
     */
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
        String currentPipelineVersion = pipeline.getVersion() != null
                ? pipeline.getVersion()
                : "1.0";

        DockerImage dockerImage = dockerImageRepository
                .findByPipelineId(pipelineId)
                .orElse(DockerImage.builder().pipeline(pipeline).build());

        // Calcul du prochain tag sémantique
        String nextTag = imageVersionService.computeNextTag(dockerImage, pipeline);

        dockerImage.setImageName(imageName);
        dockerImage.setTag(nextTag);
        dockerImage.setStatus(ImageStatus.PENDING);
        dockerImage.setBuiltAt(null);
        dockerImageRepository.save(dockerImage);

        Path buildDir = Files.createTempDirectory("pipeline-build-" + pipelineId);
        log.info("Build directory created: {}", buildDir);

        try {
            generateRulesJson(pipelineId, buildDir);
            generateDockerfile(buildDir);

            // Vérifier le daemon avant de tenter le build
            checkDockerDaemon();

            dockerImage.setStatus(ImageStatus.BUILDING);
            dockerImageRepository.save(dockerImage);

            buildDockerImage(imageName, nextTag, buildDir);

            Long sizeBytes = getImageSize(imageName + ":" + nextTag);

            // Appliquer la version sémantique sur l'entité
            imageVersionService.applyNextVersion(dockerImage, nextTag, currentPipelineVersion);

            dockerImage.setStatus(ImageStatus.SUCCESS);
            dockerImage.setBuiltAt(LocalDateTime.now());
            dockerImage.setSizeBytes(sizeBytes);
            dockerImageRepository.save(dockerImage);

            log.info("Docker image generated successfully: {}:{}", imageName, nextTag);

            return new DockerImageBuildResponse(
                    dockerImage.getId(), imageName, nextTag, "SUCCESS", "Image built successfully");

        } catch (DockerDaemonException e) {
            // Daemon injoignable — pipeline reste VALIDATED
            dockerImage.setStatus(ImageStatus.FAILED);
            dockerImageRepository.save(dockerImage);
            log.error("Docker daemon unreachable for pipeline={}: {}", pipelineId, e.getMessage());
            throw e;  // → GlobalExceptionHandler → 503

        } catch (DockerBuildException e) {
            // Build échoué — pipeline reste VALIDATED
            dockerImage.setStatus(ImageStatus.FAILED);
            dockerImageRepository.save(dockerImage);
            log.error("Docker build failed for pipeline={} (exit={}): {}",
                    pipelineId, e.getExitCode(), e.getMessage());
            throw e;  // → GlobalExceptionHandler → 422

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

    // ══════════════════════════════════════════════════════════════════════════
    //  Private helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Vérifie que le daemon Docker est joignable via {@code docker info}.
     *
     * <p>Si la commande échoue (IOException, exit code != 0), lance une
     * {@link DockerDaemonException} avec le détail technique.
     *
     * @throws DockerDaemonException si le daemon est inaccessible
     */
    private void checkDockerDaemon() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "info")
                    .redirectErrorStream(true);
            Process proc = pb.start();

            // Vider stdout pour éviter le blocage du buffer
            StringBuilder output = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exit = proc.waitFor();
            if (exit != 0) {
                String detail = output.toString().trim();
                log.error("docker info failed (exit={}): {}", exit, detail);
                throw new DockerDaemonException(
                        "Docker daemon is not reachable (docker info exited with code " + exit + ")",
                        detail);
            }

        } catch (DockerDaemonException e) {
            throw e;
        } catch (IOException e) {
            // docker binary introuvable ou socket absent
            log.error("Cannot connect to Docker daemon: {}", e.getMessage());
            throw new DockerDaemonException(
                    "Docker daemon is not reachable — cannot execute 'docker info'", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DockerDaemonException(
                    "Docker daemon check interrupted", e.getMessage());
        }
    }

    /**
     * Lance {@code docker build} de façon synchrone et accumule le log complet.
     * En cas d'exit code != 0, lance une {@link DockerBuildException} avec le log.
     * En cas d'IOException au démarrage, lance une {@link DockerDaemonException}.
     */
    private void buildDockerImage(String imageName, String tag, Path buildDir) throws Exception {
        String fullName = imageName + ":" + tag;
        List<String> command = List.of("docker", "build", "-t", fullName, buildDir.toString());
        log.info("Running: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new DockerDaemonException(
                    "Failed to start 'docker build' process", e);
        }

        StringBuilder buildLog = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                buildLog.append(line).append("\n");
                log.info("[docker build] {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new DockerBuildException(
                    "docker build failed with exit code: " + exitCode,
                    exitCode,
                    buildLog.toString());
        }
        log.info("Docker image built successfully: {}", fullName);
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

    /**
     * Émet un événement SSE {@code BUILD_FAILED} avec un corps enrichi, puis ferme l'émetteur.
     *
     * @param emitter   le SseEmitter à fermer
     * @param errorType code sémantique : {@code DAEMON_UNREACHABLE}, {@code BUILD_ERROR},
     *                  {@code PIPELINE_NOT_FOUND}, {@code PIPELINE_NOT_VALIDATED}, {@code BUILD_FAILED}
     * @param message   message lisible par le développeur
     * @param exitCode  exit code docker (null si non applicable)
     * @param buildLog  log complet du build (null si non disponible)
     */
    private void sendSseErrorAndComplete(SseEmitter emitter,
                                         String errorType,
                                         String message,
                                         Integer exitCode,
                                         String buildLog) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type",        "BUILD_FAILED");
            payload.put("errorType",   errorType);
            payload.put("errorMessage", message != null ? message : "Unknown error");
            if (exitCode != null) {
                payload.put("exitCode", exitCode);
            }
            if (buildLog != null && !buildLog.isBlank()) {
                payload.put("buildLog", buildLog);
            }
            emitter.send(SseEmitter.event()
                    .name("BUILD_FAILED")
                    .data(objectMapper.writeValueAsString(payload)));
            emitter.complete();
        } catch (IOException ignored) {
            // Client déjà déconnecté
        }
    }

    // ── Generate rules.json ───────────────────────────────────────────────────

    private void generateRulesJson(Long pipelineId, Path buildDir) throws Exception {
        List<MappingRule>    mappingRules     = mappingRuleRepository.findByPipelineIdAndActiveTrue(pipelineId);
        List<PipelineField>  validationFields = pipelineFieldRepository.findAllByPipelineId(pipelineId);
        Pipeline pipeline = pipelineRepository.findByIdWithProviders(pipelineId)   // ← changé
            .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found with id=" + pipelineId));
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
        
        //  export des providers pour dispatch autonome dans l'image ──
        List<Map<String, Object>> providersList = Optional.ofNullable(pipeline.getProviders())
            .orElse(List.of())
            .stream()
            .map(p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",       p.getId());
                m.put("name",     p.getName());
                m.put("endpoint", p.getEndpoint());
                m.put("timeout",  p.getTimeout());
                return m;
            }).toList();
            
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("pipelineId",       pipelineId);
        rules.put("mappingRules",     mappingList);
        rules.put("validationFields", validationList);
        rules.put("providers",        providersList);      // ← ajouté
        rules.put("outputFormat",     pipeline.getOutputFormat().name()); // JSON / XML pour les headers
List<ResponseMappingRule> responseRules = responseMappingRuleRepository.findByPipelineId(pipelineId);

List<Map<String, Object>> responseMappingRulesList = responseRules.stream()
        .filter(ResponseMappingRule::isActive)
        .map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("providerId",  r.getProvider() != null ? r.getProvider().getId() : null);
            m.put("sourceField", r.getSourceField());
            m.put("targetField", r.getTargetField());
            m.put("mappingType", r.getMappingType().name());
            m.put("expression",  r.getExpression());
            m.put("required",    r.isRequired());
            return m;
        }).toList();

rules.put("responseMappingRules", responseMappingRulesList);
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



// ─────────────────────────────────────────────────────────────────────────────

    /**
     * Vérifie de façon synchrone que le pipeline existe et est en statut {@code VALIDATED}.
     *
     * <p>Appelé par {@link com.miniESB.controller.BuildMonitorController#streamBuildLogs}
     * pour rejeter immédiatement les requêtes invalides avant de lancer le thread {@code @Async},
     * évitant ainsi d'ouvrir une connexion SSE pour rien.
     *
     * @param pipelineId identifiant du pipeline à valider
     * @throws ResourceNotFoundException si le pipeline n'existe pas
     * @throws IllegalStateException     si le pipeline n'est pas en statut {@code VALIDATED}
     */
    public void assertPipelineValidated(Long pipelineId) {
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));

        if (pipeline.getStatus() != PipelineStatus.VALIDATED) {
            throw new IllegalStateException(
                    "Pipeline must be VALIDATED to generate an image — current status: "
                            + pipeline.getStatus());
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// Emplacement dans le fichier : coller juste avant deleteDirectory(Path dir)
// ─────────────────────────────────────────────────────────────────────────────
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
    public List<BuildLogEntryResponse> getVersionHistory(Long pipelineId) {
    return buildLogEntryRepository.findByPipelineIdOrderByStartTimeDesc(pipelineId)
            .stream()
            .map(e -> new BuildLogEntryResponse(
                    e.getId(),
                    e.getVersion() != null ? e.getVersion() : "—",
                    e.getVersion(),
                    e.getStatus(),
                    e.getStartTime(),
                    e.getEndTime(),
                    e.getStartTime() != null && e.getEndTime() != null
                            ? Duration.between(e.getStartTime(), e.getEndTime()).toSeconds()
                            : null
            )).toList();
    }
}