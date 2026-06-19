// dto/sandbox/SandboxLogResponse.java
package com.miniESB.dto.sandbox;

import java.time.LocalDateTime;

public record SandboxLogResponse(
    Long          id,
    Long          pipelineId,
    Long          payloadId,
    boolean       validationPassed,
    boolean       mappingApplied,
    String        validationMessage,
    Long          durationMs,
    LocalDateTime executedAt,
    String        inputFormat,
    String        rawContent,
    String        failureStep,
    String        violations,
    String        originalPayload,
    String        mappedPayload,
    String        mappingSummary
) {}