package com.miniESB.dto.auditlog;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String performedBy,
        String performedByRole,
        String action,
        String targetEntity,
        String targetId,
        String details,
        Integer httpStatus,
        String errorMessage,
        String errorCode,
        Instant timestamp,
        Long    durationMs
) {}