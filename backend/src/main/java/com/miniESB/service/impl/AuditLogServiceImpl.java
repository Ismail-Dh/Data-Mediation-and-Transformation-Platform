package com.miniESB.service.impl;

import com.miniESB.domain.entity.AuditLog;
import com.miniESB.dto.auditlog.AuditEntry;
import com.miniESB.dto.auditlog.AuditLogResponse;
import com.miniESB.repository.AuditLogRepository;
import com.miniESB.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public void save(AuditEntry entry) {
        AuditLog auditLog = AuditLog.builder()
                .performedBy(entry.performedBy())
                .performedByRole(entry.performedByRole())
                .action(entry.action())
                .targetEntity(entry.targetEntity())
                .targetId(entry.targetId())
                .details(entry.details())
                .httpStatus(entry.httpStatus())
                .errorMessage(entry.errorMessage())
                .errorCode(entry.errorCode())
                .timestamp(Instant.now())
                .build();
        auditLogRepository.save(auditLog);
    }

    @Override
    public List<AuditLogResponse> getAllLogs(String username, String role,
                                             String action, Integer httpStatus) {
        return auditLogRepository
                .findByFilters(username, role, action, httpStatus)
                .stream()
                .map(this::toResponse)
                .toList();
    }
    @Override
    public List<AuditLogResponse> getMyLogs(String username) {
       return auditLogRepository
            .findByFilters(username, null, null, null)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    // ─── Purge automatique tous les jours à 2h ────────────────────────────────

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeOldLogs() {
        Instant limit = Instant.now().minus(90, ChronoUnit.DAYS);
        auditLogRepository.deleteByTimestampBefore(limit);
        log.info("[AUDIT] Logs purged before {}", limit);
    }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getPerformedBy(),
                log.getPerformedByRole(),
                log.getAction(),
                log.getTargetEntity(),
                log.getTargetId(),
                log.getDetails(),
                log.getHttpStatus(),
                log.getErrorMessage(),
                log.getErrorCode(),
                log.getTimestamp()
        );
    }
}