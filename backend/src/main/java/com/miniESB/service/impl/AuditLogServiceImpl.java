package com.miniESB.service.impl;

import com.miniESB.domain.entity.AuditLog;
import com.miniESB.dto.auditlog.AuditLogResponse;
import com.miniESB.repository.AuditLogRepository;
import com.miniESB.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public void save(String performedBy, String performedByRole, String action,
                     String targetEntity, String targetId, String details) {
        AuditLog log = AuditLog.builder()
                .performedBy(performedBy)
                .performedByRole(performedByRole)
                .action(action)
                .targetEntity(targetEntity)
                .targetId(targetId)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
        auditLogRepository.save(log);
    }

    @Override
    public List<AuditLogResponse> getAllLogs(String username, String role) {
        List<AuditLog> logs;

        if (username != null) {
            logs = auditLogRepository.findByPerformedBy(username);
        } else if (role != null) {
            logs = auditLogRepository.findByPerformedByRole(role);
        } else {
            logs = auditLogRepository.findAll();
        }

        return logs.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getPerformedBy(),
                log.getPerformedByRole(),
                log.getAction(),
                log.getTargetEntity(),
                log.getTargetId(),
                log.getDetails(),
                log.getTimestamp()
        );
    }
}