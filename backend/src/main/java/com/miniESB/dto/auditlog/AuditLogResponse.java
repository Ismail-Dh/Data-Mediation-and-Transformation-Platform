package com.miniESB.dto.auditlog;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        String performedBy,
        String performedByRole,
        String action,
        String targetEntity,
        String targetId,
        String details,
        LocalDateTime timestamp
) {}