package com.miniESB.controller;

import com.miniESB.dto.docker.DockerImageBuildResponse;
import com.miniESB.dto.docker.DockerImageResponse;
import com.miniESB.service.impl.DockerImageGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Docker Image", description = "Generate and download Docker images for validated pipelines")
@RestController
@RequestMapping("/api/pipelines/{pipelineId}/image")
@PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
@RequiredArgsConstructor
public class DockerImageController {

    private final DockerImageGeneratorService generatorService;

    @Operation(summary = "Generate a Docker image for a validated pipeline")
    @PostMapping("/generate")
    public ResponseEntity<DockerImageBuildResponse> generate(
            @PathVariable Long pipelineId) throws Exception {
        return ResponseEntity.ok(generatorService.generateImage(pipelineId));
    }

    @Operation(summary = "Get Docker image info for a pipeline")
    @GetMapping
    public ResponseEntity<DockerImageResponse> getImageInfo(
            @PathVariable Long pipelineId) {
        return ResponseEntity.ok(generatorService.getImageInfo(pipelineId));
    }

    @Operation(summary = "Download Docker image as tar archive")
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(
            @PathVariable Long pipelineId) throws Exception {
        byte[] imageBytes = generatorService.exportImage(pipelineId);
        String filename   = "pipeline-" + pipelineId + ".tar";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(imageBytes.length)
                .body(imageBytes);
    }
}