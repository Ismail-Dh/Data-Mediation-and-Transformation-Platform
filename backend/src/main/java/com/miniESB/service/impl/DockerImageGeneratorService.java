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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerImageGeneratorService {

    private final PipelineRepository      pipelineRepository;
    private final MappingRuleRepository   mappingRuleRepository;
    private final PipelineFieldRepository pipelineFieldRepository;
    private final DockerImageRepository   dockerImageRepository;
    private final ObjectMapper            objectMapper;

    // ── Generate ──────────────────────────────────────────────────────────────

    @Transactional
    public DockerImageBuildResponse generateImage(Long pipelineId) throws Exception {

        // 1 — pipeline existe et est VALIDATED
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));

        if (pipeline.getStatus() != PipelineStatus.VALIDATED) {
            throw new IllegalStateException(
                    "Pipeline must be VALIDATED to generate an image — current status: "
                    + pipeline.getStatus());
        }

        // 2 — créer ou mettre à jour l'entrée DockerImage en PENDING
        String imageName = "pipeline-" + pipelineId;
        String tag       = "latest";

        DockerImage dockerImage = dockerImageRepository
                .findByPipelineId(pipelineId)
                .orElse(DockerImage.builder()
                        .pipeline(pipeline)
                        .build());

        dockerImage.setImageName(imageName);
        dockerImage.setTag(tag);
        dockerImage.setStatus(ImageStatus.PENDING);
        dockerImage.setBuiltAt(null);
        dockerImageRepository.save(dockerImage);

        // 3 — générer les fichiers dans un dossier temp
        Path buildDir = Files.createTempDirectory("pipeline-build-" + pipelineId);
        log.info("Build directory created: {}", buildDir);

        try {
            // 3a — générer rules.json
            generateRulesJson(pipelineId, buildDir);

            // 3b — générer Dockerfile
            generateDockerfile(buildDir);

            // 3c — lancer docker build
            dockerImage.setStatus(ImageStatus.BUILDING);
            dockerImageRepository.save(dockerImage);

            buildDockerImage(imageName, tag, buildDir);

            // 3d — récupérer la taille de l'image
            Long sizeBytes = getImageSize(imageName + ":" + tag);

            // 4 — mettre à jour le statut
            dockerImage.setStatus(ImageStatus.SUCCESS);
            dockerImage.setBuiltAt(LocalDateTime.now());
            dockerImage.setSizeBytes(sizeBytes);
            dockerImageRepository.save(dockerImage);

            log.info("Docker image generated successfully: {}:{}", imageName, tag);

            return new DockerImageBuildResponse(
                    dockerImage.getId(),
                    imageName,
                    tag,
                    "SUCCESS",
                    "Image built successfully"
            );

        } catch (Exception e) {
            dockerImage.setStatus(ImageStatus.FAILED);
            dockerImageRepository.save(dockerImage);
            log.error("Docker image generation FAILED for pipeline={}: {}",
                    pipelineId, e.getMessage());
            throw e;

        } finally {
            // Nettoyer le dossier temp
            deleteDirectory(buildDir);
        }
    }

    // ── Generate rules.json ───────────────────────────────────────────────────

    private void generateRulesJson(Long pipelineId, Path buildDir) throws Exception {
        List<MappingRule> mappingRules =
                mappingRuleRepository.findByPipelineIdAndActiveTrue(pipelineId);
        List<PipelineField> validationFields =
                pipelineFieldRepository.findAllByPipelineId(pipelineId);

        // Construire la structure des règles
        List<Map<String, Object>> mappingList = mappingRules.stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("sourceField",  r.getSourceField());
                    m.put("targetField",  r.getTargetField());
                    m.put("mappingType",  r.getMappingType().name());
                    m.put("expression",   r.getExpression());
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

        // Écrire rules.json
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

    // ── Docker build ──────────────────────────────────────────────────────────

    private void buildDockerImage(String imageName, String tag,
                                   Path buildDir) throws Exception {
        String fullName = imageName + ":" + tag;
        List<String> command = List.of(
                "docker", "build",
                "-t", fullName,
                buildDir.toString()
        );

        log.info("Running: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Lire les logs du build
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[docker build] {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException(
                    "docker build failed with exit code: " + exitCode);
        }
        log.info("Docker image built successfully: {}", fullName);
    }

    // ── Get image size ────────────────────────────────────────────────────────

    private Long getImageSize(String imageFullName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "inspect",
                    "--format={{.Size}}",
                    imageFullName
            );
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

    // ── Download image ────────────────────────────────────────────────────────

    public byte[] exportImage(Long pipelineId) throws Exception {
        DockerImage dockerImage = dockerImageRepository.findByPipelineId(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No image found for pipeline id=" + pipelineId));

        if (dockerImage.getStatus() != ImageStatus.SUCCESS) {
            throw new IllegalStateException(
                    "Image is not ready — current status: " + dockerImage.getStatus());
        }

        String fullName = dockerImage.getImageName() + ":" + dockerImage.getTag();

        // docker save → gzip → byte array
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

    // ── Helper ────────────────────────────────────────────────────────────────

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