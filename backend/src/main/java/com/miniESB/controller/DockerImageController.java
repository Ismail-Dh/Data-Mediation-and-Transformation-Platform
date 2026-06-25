package com.miniESB.controller;

import com.miniESB.domain.entity.DockerImage;
import com.miniESB.dto.docker.*;
import com.miniESB.exception.DockerBuildException;
import com.miniESB.exception.DockerDaemonException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.DockerImageRepository;
import com.miniESB.service.impl.DockerImageGeneratorService;
import com.miniESB.service.impl.DockerImagePushService;
import com.miniESB.service.ImageVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour la génération et l'export d'images Docker.
 *
 * <h3>Garantie pipeline sur erreur</h3>
 * <p>Quelle que soit l'erreur de build, le statut du pipeline reste
 * {@code VALIDATED}. Seul le {@link com.miniESB.domain.entity.DockerImage}
 * associé passe en {@code FAILED}.
 *
 * <h3>Codes d'erreur</h3>
 * <ul>
 *   <li><strong>503</strong> — {@code DAEMON_UNREACHABLE} : le daemon Docker est injoignable</li>
 *   <li><strong>422</strong> — {@code BUILD_ERROR}        : {@code docker build} a échoué (exit != 0)</li>
 *   <li><strong>422</strong> — {@code PIPELINE_NOT_VALIDATED} : le pipeline n'est pas en statut VALIDATED</li>
 *   <li><strong>404</strong> — pipeline ou image introuvable</li>
 * </ul>
 */
@Tag(name = "Docker Image", description = "Generate and download Docker images for validated pipelines")
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/api/pipelines/{pipelineId}/image")
@PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
@RequiredArgsConstructor
public class DockerImageController {

    private final DockerImageGeneratorService generatorService;
    private final ImageVersionService         imageVersionService;
    private final DockerImageRepository       dockerImageRepository;
    private final DockerImagePushService pushService; 


    // ── POST /generate ────────────────────────────────────────────────────────

    @Operation(summary = "Generate a Docker image for a validated pipeline",
            description = """
                   Builds a Docker image from the pipeline's mapping rules.

                   **Pipeline status guarantee**: if the build fails for any reason
                   (daemon unreachable or build error), the pipeline stays VALIDATED.
                   Only the DockerImage record is set to FAILED.
                   """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image built successfully"),
            @ApiResponse(responseCode = "503",
                    description = "Docker daemon unreachable — pipeline remains VALIDATED",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                             {
                               "error": "Docker daemon is not reachable",
                               "errorType": "DAEMON_UNREACHABLE",
                               "technicalDetail": "Cannot connect to the Docker daemon at unix:///var/run/docker.sock",
                               "timestamp": "2025-06-15T10:00:00Z",
                               "hint": "Ensure the Docker daemon is running and the socket is accessible."
                             }"""))),
            @ApiResponse(responseCode = "422",
                    description = "docker build failed — pipeline remains VALIDATED",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                             {
                               "error": "Docker build failed",
                               "errorType": "BUILD_ERROR",
                               "exitCode": 1,
                               "buildLog": "Step 1/3 : FROM mini-esb-backend:latest\\n... ERROR ...",
                               "timestamp": "2025-06-15T10:01:00Z"
                             }""")))
    })
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@PathVariable Long pipelineId) {
        try {
            DockerImageBuildResponse response = generatorService.generateImage(pipelineId);
            return ResponseEntity.ok(response);

        } catch (DockerDaemonException e) {
            // 503 — daemon injoignable, pipeline reste VALIDATED
            Map<String, Object> body = buildDaemonErrorBody(e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);

        } catch (DockerBuildException e) {
            // 422 — build échoué, pipeline reste VALIDATED
            Map<String, Object> body = buildBuildErrorBody(e);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);

        } catch (IllegalStateException e) {
            // 422 — pipeline pas en état VALIDATED
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error",     e.getMessage());
            body.put("errorType", "PIPELINE_NOT_VALIDATED");
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);

        } catch (Exception e) {
            // 500 fallback
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error",     "Image generation failed: " + e.getMessage());
            body.put("errorType", "BUILD_FAILED");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    // ── GET / (image info) ────────────────────────────────────────────────────

    @Operation(summary = "Get Docker image info for a pipeline")
    @GetMapping
    public ResponseEntity<DockerImageResponse> getImageInfo(@PathVariable Long pipelineId) {
        return ResponseEntity.ok(generatorService.getImageInfo(pipelineId));
    }

    // ── GET /download ─────────────────────────────────────────────────────────

    @Operation(summary = "Download Docker image as tar archive")
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@PathVariable Long pipelineId) throws Exception {
        byte[] imageBytes = generatorService.exportImage(pipelineId);
        String filename   = "pipeline-" + pipelineId + ".tar";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(imageBytes.length)
                .body(imageBytes);
    }

    // ── POST /version/bump ────────────────────────────────────────────────────

    /**
     * Bump manuel de version MINOR ou MAJOR sur le pipeline.
     * Le patch sera automatiquement remis à 0 au prochain build.
     *
     * POST /api/pipelines/{pipelineId}/image/version/bump
     */
    @Operation(summary = "Manually bump MINOR or MAJOR version for a pipeline image")
    @PostMapping("/version/bump")
    public ResponseEntity<VersionBumpResponse> bumpVersion(
            @PathVariable Long pipelineId,
            @RequestBody @Valid VersionBumpRequest request) {

        VersionBumpResponse response = imageVersionService.bumpVersion(pipelineId, request);
        return ResponseEntity.ok(response);
    }

    // ── GET /version ──────────────────────────────────────────────────────────

    /**
     * Retourne la version sémantique actuelle de l'image d'un pipeline.
     *
     * GET /api/pipelines/{pipelineId}/image/version
     */
    @Operation(summary = "Get the current semantic version of a pipeline image")
    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> getCurrentVersion(
            @PathVariable Long pipelineId) {

        DockerImage image = dockerImageRepository.findByPipelineId(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No image found for pipeline id=" + pipelineId));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("pipelineId",          pipelineId);
        response.put("currentTag",          image.getTag());
        response.put("pipelineVersion",     image.getPipeline().getVersion());
        response.put("patch",               image.getVersionPatch());
        response.put("lastPipelineVersion", image.getLastPipelineVersion());

        return ResponseEntity.ok(response);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, Object> buildDaemonErrorBody(DockerDaemonException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error",     "Docker daemon is not reachable");
        body.put("errorType", "DAEMON_UNREACHABLE");
        if (e.getTechnicalDetail() != null) {
            body.put("technicalDetail", e.getTechnicalDetail());
        }
        body.put("timestamp", java.time.Instant.now().toString());
        body.put("hint",      "Ensure the Docker daemon is running and the socket is accessible.");
        return body;
    }

    private Map<String, Object> buildBuildErrorBody(DockerBuildException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error",     "Docker build failed");
        body.put("errorType", "BUILD_ERROR");
        body.put("exitCode",  e.getExitCode());
        if (e.getBuildLog() != null && !e.getBuildLog().isBlank()) {
            body.put("buildLog", e.getBuildLog());
        }
        body.put("timestamp", java.time.Instant.now().toString());
        return body;
    }
    @Operation(summary = "Get version history for a pipeline")
    @GetMapping("/versions")
    public ResponseEntity<List<BuildLogEntryResponse>> getVersionHistory(
        @PathVariable Long pipelineId) {
      return ResponseEntity.ok(generatorService.getVersionHistory(pipelineId));
    }
    @Operation(summary = "Push pipeline image to a Docker registry")
@PostMapping("/push")
public ResponseEntity<?> push(
        @PathVariable Long pipelineId,
        @Valid @RequestBody PushImageRequest request) {
    try {
        return ResponseEntity.ok(
                pushService.pushImage(pipelineId, request.registryId()));
    } catch (IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", e.getMessage(), "errorType", "IMAGE_NOT_READY"));
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Push failed: " + e.getMessage(),
                             "errorType", "PUSH_FAILED"));
    }
}

}