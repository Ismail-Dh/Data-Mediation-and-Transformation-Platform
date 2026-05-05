package com.miniESB.repository;

import com.miniESB.domain.entity.AuditLog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByPerformedBy(String performedBy);
    List<AuditLog> findByPerformedByRole(String performedByRole);
}