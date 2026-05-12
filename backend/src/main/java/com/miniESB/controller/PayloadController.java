package com.miniESB.controller;

import com.miniESB.dto.payload.PayloadRequest;
import com.miniESB.dto.payload.PayloadResponse;
import com.miniESB.service.PayloadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Payloads", description = "Submit and retrieve payloads for a pipeline")
@RestController
@RequestMapping("/api/pipelines/{pipelineId}/payloads")
@PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
@RequiredArgsConstructor
public class PayloadController {

    private final PayloadService payloadService;

    @Operation(summary = "Submit a payload to a pipeline",
               description = "Validates the payload structure against the pipeline schema. Returns 422 if violations found.")
    @PostMapping
    public ResponseEntity<PayloadResponse> receivePayload(
            @PathVariable Long pipelineId,
            @Valid @RequestBody PayloadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(payloadService.receivePayload(pipelineId, request));
    }

    @Operation(summary = "Get all payloads of a pipeline")
    @GetMapping
    public ResponseEntity<List<PayloadResponse>> getPayloads(@PathVariable Long pipelineId) {
        return ResponseEntity.ok(payloadService.getPayloadsByPipeline(pipelineId));
    }

    @Operation(summary = "Get a payload by ID")
    @GetMapping("/{payloadId}")
    public ResponseEntity<PayloadResponse> getPayload(@PathVariable Long pipelineId,
                                                       @PathVariable Long payloadId) {
        return ResponseEntity.ok(payloadService.getPayloadById(payloadId));
    }
}