package com.miniESB.controller;

import com.miniESB.dto.Pipeline.PipelineResponse;
import com.miniESB.dto.sandbox.SandboxRequest;
import com.miniESB.dto.sandbox.SandboxResponse;
import com.miniESB.service.SandboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Sandbox", description = "Test a pipeline end-to-end without sending to external systems")
@RestController
@RequestMapping("/api/pipelines/{pipelineId}/sandbox")
@PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
@RequiredArgsConstructor
public class SandboxController {

    private final SandboxService sandboxService;

    @Operation(
        summary = "Run a sandbox test on a pipeline",
        description = "Submits a raw payload through validation and mapping. " +
                      "If all steps pass, the pipeline status is updated to VALIDATED."
    )
    @PostMapping("/run")
    public ResponseEntity<SandboxResponse> run(
            @PathVariable Long pipelineId,
            @Valid @RequestBody SandboxRequest request) {
        return ResponseEntity.ok(sandboxService.run(pipelineId, request));
    }
   
}