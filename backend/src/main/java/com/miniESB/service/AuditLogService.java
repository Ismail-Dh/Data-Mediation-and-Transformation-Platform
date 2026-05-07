package com.miniESB.service;

import com.miniESB.dto.auditlog.AuditEntry;
import com.miniESB.dto.auditlog.AuditLogResponse;

import java.util.List;

public interface AuditLogService {
    void save(AuditEntry entry);
    List<AuditLogResponse> getAllLogs(String username, String role,
                                      String action, Integer httpStatus);
    List<AuditLogResponse> getMyLogs(String username);
}