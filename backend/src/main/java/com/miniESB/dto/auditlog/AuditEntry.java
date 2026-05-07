package com.miniESB.dto.auditlog;

public record AuditEntry(
        String performedBy,
        String performedByRole,
        String action,
        String targetEntity,
        String targetId,
        String details,
        Integer httpStatus,
        String errorMessage,
        String errorCode
) {}