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
import com.miniESB.service.ImageVersionService;
import com.miniESB.service.docker.CommandExecutor;
import com.miniESB.service.docker.CommandResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestration du build d'image Docker pour un pipeline (synchrone et SSE).
 *
 * <p><strong>Depuis le refactoring</strong> :</p>
 * <ul>
 *   <li>la génération des artefacts ({@code rules.json}, {@code Dockerfile}) est
 *       déléguée à {@link DockerBuildArtifactGenerator} (SRP) ;</li>
 *   <li>l'exécution des commandes {@code docker ...} passe par l'abstraction
 *       {@link CommandExecutor} au lieu d'instancier {@code ProcessBuilder}
 *       directement (Dependency Inversion Principle) — cette classe est
 *       désormais testable avec un {@code CommandExecutor} simulé, sans
 *       nécessiter de vrai daemon Docker.</li>
 * </ul>
 *
 * <p><strong>Garantie pipeline sur erreur</strong> : quelle que soit l'erreur
 * (daemon injoignable ou build échoué), le statut du pipeline reste
 * {@code VALIDATED}. Seul le {@link DockerImage} passe en {@code FAILED}.</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class DockerImageGeneratorService {

    private final PipelineRepository pipelineRepository;
    private final DockerImageRepository dockerImageRepository;
    private final BuildLogEntryRepository buildLogEntryRepository;
    private final ObjectMapper objectMapper;
    private final ImageVersionService imageVersionService;
    private final DockerBuildArtifactGenerator artifactGenerator;
    private final CommandExecutor commandExecutor;

    // ══════════════════════════════════════════════════════════════════════════
    //  SSE streaming build
    // ══════════════════════════════════════════════════════════════════════════

    @Async("buildSseExecutor")
    public void generateImageWithSse(Long pipelineId, SseEmitter emitter) {

        LocalDateTime startTime = LocalDateTime.now();

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

        String imageName = "pipeline-" + pipelineId;
        String currentPipelineVersion = pipeline.getVersion() != null ? pipeline.getVersion() : "1.0";

        DockerImage dockerImage = dockerImageRepository.findByPipelineId(pipelineId)
                .orElse(DockerImage.builder().pipeline(pipeline).build());

        String nextTag = imageVersionService.computeNextTag(dockerImage, pipeline);

        dockerImage.setImageName(imageName);
        dockerImage.setTag(nextTag);
        dockerImage.setStatus(ImageStatus.BUILDING);
        dockerImage.setBuiltAt(null);
        dockerImageRepository.save(dockerImage);

        Path buildDir = null;
        StringBuilder fullLog = new StringBuilder();
        try {
            buildDir = Files.createTempDirectory("pipeline-build-sse-" + pipelineId);

            artifactGenerator.generateRulesJson(pipelineId, buildDir);
            artifactGenerator.generateDockerfile(buildDir);

            String fullImageName = imageName + ":" + nextTag;

            checkDockerDaemon();

            CommandResult result;
            try {
                result = commandExecutor.run(
                        List.of("docker", "build", "-t", fullImageName, buildDir.toString()),
                        line -> {
                            fullLog.append(line).append("\n");
                            try {
                                emitter.send(line, MediaType.TEXT_PLAIN);
                            } catch (IOException ignored) {
                                // client déconnecté — on laisse le build continuer côté serveur
                            }
                        });
            } catch (IOException e) {
                throw new DockerDaemonException("Failed to start docker build process", e);
            }

            if (!result.isSuccess()) {
                dockerImage.setStatus(ImageStatus.FAILED);
                dockerImageRepository.save(dockerImage);
                saveBuildLogEntry(pipeline, dockerImage, result.output(),
                        ImageStatus.FAILED, startTime, LocalDateTime.now());

                sendSseErrorAndComplete(emitter, "BUILD_ERROR",
                        "docker build failed with exit code: " + result.exitCode(),
                        result.exitCode(), result.output());
                return;
            }

            Long sizeBytes = getImageSize(imageName + ":" + nextTag);
            LocalDateTime endTime = LocalDateTime.now();

            imageVersionService.applyNextVersion(dockerImage, nextTag, currentPipelineVersion);

            dockerImage.setStatus(ImageStatus.SUCCESS);
            dockerImage.setBuiltAt(endTime);
            dockerImage.setSizeBytes(sizeBytes);
            dockerImageRepository.save(dockerImage);

            saveBuildLogEntry(pipeline, dockerImage, result.output(),
                    ImageStatus.SUCCESS, startTime, endTime);

            long durationSec = Duration.between(startTime, endTime).toSeconds();
            Map<String, Object> completeEvent = new LinkedHashMap<>();
            completeEvent.put("type", "BUILD_COMPLETE");
            completeEvent.put("imageId", dockerImage.getId());
            completeEvent.put("tag", nextTag);
            completeEvent.put("size", sizeBytes != null ? sizeBytes : 0L);
            completeEvent.put("duration", durationSec);

            emitter.send(SseEmitter.event()
                    .name("BUILD_COMPLETE")
                    .data(objectMapper.writeValueAsString(completeEvent)));
            emitter.complete();

            log.info("SSE build complete for pipeline={} in {}s", pipelineId, durationSec);

        } catch (DockerDaemonException e) {
            log.error("Docker daemon unreachable for pipeline={}: {}", pipelineId, e.getMessage());
            dockerImage.setStatus(ImageStatus.FAILED);
            dockerImageRepository.save(dockerImage);
            saveBuildLogEntry(pipeline, dockerImage, fullLog.toString(),
                    ImageStatus.FAILED, startTime, LocalDateTime.now());

            sendSseErrorAndComplete(emitter, "DAEMON_UNREACHABLE",
                    e.getMessage() + (e.getTechnicalDetail() != null ? " — " + e.getTechnicalDetail() : ""),
                    null, null);

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
    //  Build synchrone (endpoint POST /generate)
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
        String currentPipelineVersion = pipeline.getVersion() != null ? pipeline.getVersion() : "1.0";

        DockerImage dockerImage = dockerImageRepository.findByPipelineId(pipelineId)
                .orElse(DockerImage.builder().pipeline(pipeline).build());

        String nextTag = imageVersionService.computeNextTag(dockerImage, pipeline);

        dockerImage.setImageName(imageName);
        dockerImage.setTag(nextTag);
        dockerImage.setStatus(ImageStatus.PENDING);
        dockerImage.setBuiltAt(null);
        dockerImageRepository.save(dockerImage);

        Path buildDir = Files.createTempDirectory("pipeline-build-" + pipelineId);
        log.info("Build directory created: {}", buildDir);

        try {
            artifactGenerator.generateRulesJson(pipelineId, buildDir);
            artifactGenerator.generateDockerfile(buildDir);

            checkDockerDaemon();

            dockerImage.setStatus(ImageStatus.BUILDING);
            dockerImageRepository.save(dockerImage);

            buildDockerImage(imageName, nextTag, buildDir);

            Long sizeBytes = getImageSize(imageName + ":" + nextTag);

            imageVersionService.applyNextVersion(dockerImage, nextTag, currentPipelineVersion);

            dockerImage.setStatus(ImageStatus.SUCCESS);
            dockerImage.setBuiltAt(LocalDateTime.now());
            dockerImage.setSizeBytes(sizeBytes);
            dockerImageRepository.save(dockerImage);

            log.info("Docker image generated successfully: {}:{}", imageName, nextTag);

            return new DockerImageBuildResponse(
                    dockerImage.getId(), imageName, nextTag, "SUCCESS", "Image built successfully");

        } catch (DockerDaemonException e) {
            dockerImage.setStatus(ImageStatus.FAILED);
            dockerImageRepository.save(dockerImage);
            log.error("Docker daemon unreachable for pipeline={}: {}", pipelineId, e.getMessage());
            throw e;

        } catch (DockerBuildException e) {
            dockerImage.setStatus(ImageStatus.FAILED);
            dockerImageRepository.save(dockerImage);
            log.error("Docker build failed for pipeline={} (exit={}): {}",
                    pipelineId, e.getExitCode(), e.getMessage());
            throw e;

        } catch (Exception e) {
            dockerImage.setStatus(ImageStatus.FAILED);
            dockerImageRepository.save(dockerImage);
            log.error("Docker image generation FAILED for pipeline={}: {}", pipelineId, e.getMessage());
            throw e;

        } finally {
            deleteDirectory(buildDir);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Private helpers — exécution via CommandExecutor (plus de ProcessBuilder direct)
    // ══════════════════════════════════════════════════════════════════════════

    private void checkDockerDaemon() {
        try {
            CommandResult result = commandExecutor.run(List.of("docker", "info"));
            if (!result.isSuccess()) {
                log.error("docker info failed (exit={}): {}", result.exitCode(), result.output());
                throw new DockerDaemonException(
                        "Docker daemon is not reachable (docker info exited with code " + result.exitCode() + ")",
                        result.output().trim());
            }
        } catch (DockerDaemonException e) {
            throw e;
        } catch (IOException e) {
            log.error("Cannot connect to Docker daemon: {}", e.getMessage());
            throw new DockerDaemonException("Docker daemon is not reachable — cannot execute 'docker info'", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DockerDaemonException("Docker daemon check interrupted", e.getMessage());
        }
    }

    private void buildDockerImage(String imageName, String tag, Path buildDir) throws Exception {
        String fullName = imageName + ":" + tag;
        List<String> command = List.of("docker", "build", "-t", fullName, buildDir.toString());
        log.info("Running: {}", String.join(" ", command));

        CommandResult result;
        try {
            result = commandExecutor.run(command, line -> log.info("[docker build] {}", line));
        } catch (IOException e) {
            throw new DockerDaemonException("Failed to start 'docker build' process", e);
        }

        if (!result.isSuccess()) {
            throw new DockerBuildException(
                    "docker build failed with exit code: " + result.exitCode(),
                    result.exitCode(),
                    result.output());
        }
        log.info("Docker image built successfully: {}", fullName);
    }

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
            log.warn("Could not persist BuildLogEntry for pipeline={}: {}", pipeline.getId(), ex.getMessage());
        }
    }

    private void sendSseErrorAndComplete(SseEmitter emitter, String errorType, String message,
                                          Integer exitCode, String buildLog) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "BUILD_FAILED");
            payload.put("errorType", errorType);
            payload.put("errorMessage", message != null ? message : "Unknown error");
            if (exitCode != null) payload.put("exitCode", exitCode);
            if (buildLog != null && !buildLog.isBlank()) payload.put("buildLog", buildLog);

            emitter.send(SseEmitter.event().name("BUILD_FAILED").data(objectMapper.writeValueAsString(payload)));
            emitter.complete();
        } catch (IOException ignored) {
            // Client déjà déconnecté
        }
    }

    private Long getImageSize(String imageFullName) {
        try {
            CommandResult result = commandExecutor.run(
                    List.of("docker", "inspect", "--format={{.Size}}", imageFullName));
            String line = result.output().lines().findFirst().orElse(null);
            if (line != null && !line.isBlank()) {
                return Long.parseLong(line.trim());
            }
        } catch (Exception e) {
            log.warn("Could not retrieve image size: {}", e.getMessage());
        }
        return null;
    }

    public byte[] exportImage(Long pipelineId) throws Exception {
        DockerImage dockerImage = dockerImageRepository.findByPipelineId(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No image found for pipeline id=" + pipelineId));

        if (dockerImage.getStatus() != ImageStatus.SUCCESS) {
            throw new IllegalStateException(
                    "Image is not ready — current status: " + dockerImage.getStatus());
        }

        String fullName = dockerImage.getImageName() + ":" + dockerImage.getTag();
        byte[] imageBytes = commandExecutor.runForBytes(List.of("docker", "save", fullName));

        log.info("Image exported: {} ({} bytes)", fullName, imageBytes.length);
        return imageBytes;
    }

    /**
     * Informations de version sémantique courante pour un pipeline.
     *
     * <p>Extrait de {@code DockerImageController}, qui accédait directement à
     * {@code DockerImageRepository} pour construire cette réponse — violation
     * du Dependency Inversion Principle (un contrôleur doit dépendre d'une
     * abstraction de service, pas d'un détail de persistance).</p>
     */
    public Map<String, Object> getCurrentVersionInfo(Long pipelineId) {
        DockerImage image = dockerImageRepository.findByPipelineId(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No image found for pipeline id=" + pipelineId));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("pipelineId", pipelineId);
        response.put("currentTag", image.getTag());
        response.put("pipelineVersion", image.getPipeline().getVersion());
        response.put("patch", image.getVersionPatch());
        response.put("lastPipelineVersion", image.getLastPipelineVersion());
        return response;
    }

    public DockerImageResponse getImageInfo(Long pipelineId) {
        DockerImage image = dockerImageRepository.findByPipelineId(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No image found for pipeline id=" + pipelineId));
        return toResponse(image);
    }

    /**
     * Vérifie de façon synchrone que le pipeline existe et est en statut {@code VALIDATED}.
     * Appelé par {@code BuildMonitorController} avant de lancer le thread {@code @Async}.
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

    /** Historique des builds d'un pipeline (du plus récent au plus ancien). */
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
