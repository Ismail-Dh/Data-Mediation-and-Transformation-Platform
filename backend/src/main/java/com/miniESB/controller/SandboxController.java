package com.miniESB.controller;

import com.miniESB.audit.Auditable;
import com.miniESB.domain.entity.SandboxLog;
import com.miniESB.dto.sandbox.*;
import com.miniESB.repository.SandboxLogRepository;
import com.miniESB.service.SandboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Sandbox")
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/api/pipelines/{pipelineId}/sandbox")
@PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
@RequiredArgsConstructor
public class SandboxController {

    private final SandboxService       sandboxService;
    private final SandboxLogRepository sandboxLogRepository;

    @PostMapping("/run")
    @Auditable(action = "RUN", targetEntity = "Sandbox")
    public ResponseEntity<SandboxResponse> run(
            @PathVariable Long pipelineId,
            @Valid @RequestBody SandboxRequest request) {
        return ResponseEntity.ok(sandboxService.run(pipelineId, request));
    }

    @Operation(summary = "Get paginated sandbox execution history")
    @GetMapping("/logs")
    @Auditable(action = "READ_ALL", targetEntity = "Sandbox")
    public ResponseEntity<Page<SandboxLogResponse>> getLogs(
            @PathVariable Long pipelineId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<SandboxLogResponse> logs = sandboxLogRepository
                .findByPipelineIdOrderByExecutedAtDesc(pipelineId, PageRequest.of(page, size))
                .map(l -> new SandboxLogResponse(
                        l.getId(),
                        l.getPipeline().getId(),
                        l.getPayload() != null ? l.getPayload().getId() : null,
                        l.isValidationPassed(),
                        l.isMappingApplied(),
                        l.getValidationMessage(),
                        l.getDurationMs(),
                        l.getExecutedAt(),
                        l.getInputFormat(),
                        l.getRawContent(),
                        l.getFailureStep(),
                        l.getViolations(),
                        l.getOriginalPayload(),
                        l.getMappedPayload(),
                        l.getMappingSummary()
                ));
        return ResponseEntity.ok(logs);
    }
}