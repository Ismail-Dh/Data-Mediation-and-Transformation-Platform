package com.miniESB.repository;

import com.miniESB.domain.entity.AuditLog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
@Repository
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = false)

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:username IS NULL OR a.performedBy = :username) AND " +
           "(:role IS NULL OR a.performedByRole = :role) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:httpStatus IS NULL OR a.httpStatus = :httpStatus)")
    List<AuditLog> findByFilters(
            @Param("username")   String username,
            @Param("role")       String role,
            @Param("action")     String action,
            @Param("httpStatus") Integer httpStatus
    );

    void deleteByTimestampBefore(Instant limit);
}