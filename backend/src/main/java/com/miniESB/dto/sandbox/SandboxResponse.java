package com.miniESB.dto.sandbox;

import com.miniESB.domain.enums.PayloadStatus;
import com.miniESB.domain.enums.PipelineStatus;

import java.util.Map;

// Full detail of each step — shown to the user after sandbox execution
public record SandboxResponse(
    Long pipelineId,
    Long payloadId,

    // Step 1 — Validation
    boolean validationPassed,
    String  validationMessage,

    // Step 2 — Mapping
    boolean mappingApplied,
    Map<String, Object> originalPayload,
    Map<String, Object> mappedPayload,

    // Final statuses
    PayloadStatus  payloadStatus,
    PipelineStatus pipelineStatus
) {}