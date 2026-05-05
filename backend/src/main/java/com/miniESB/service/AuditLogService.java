package com.miniESB.service;

import com.miniESB.dto.auditlog.AuditLogResponse;
import java.util.List;

public interface AuditLogService {
    void save(String performedBy, String performedByRole, String action,
              String targetEntity, String targetId, String details);
    List<AuditLogResponse> getAllLogs(String username, String role);
}