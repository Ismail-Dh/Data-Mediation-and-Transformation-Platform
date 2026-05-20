package com.miniESB.repository;

import com.miniESB.domain.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Dedicated repository for monitoring/KPI aggregation queries.
 * Keeps monitoring concerns separate from the generic AuditLogRepository.
 */
public interface MonitoringRepository extends JpaRepository<AuditLog, Long> {

    // ── Global counts ─────────────────────────────────────────────────────────

    @Query("SELECT COUNT(a) FROM AuditLog a")
    long countAllRequests();

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.httpStatus >= 400")
    long countErrors();

    /**
     * AVG duration — returns null when no rows have duration_ms recorded yet.
     * The column is nullable by design until AuditAspect starts measuring time.
     */
    @Query("SELECT AVG(a.durationMs) FROM AuditLog a WHERE a.durationMs IS NOT NULL")
    Double avgDurationMs();

    // ── Hourly time-series for the last 24 h ──────────────────────────────────

    /**
     * Returns [hour_string, count] pairs for every hour bucket in the last 24 h.
     * hour_string format: "2025-05-20T14" (ISO truncated to hour).
     */
    @Query(value = """
            SELECT TO_CHAR(DATE_TRUNC('hour', timestamp AT TIME ZONE 'UTC'), 'YYYY-MM-DD"T"HH24') AS hour,
                   COUNT(*) AS cnt
            FROM audit_logs
            WHERE timestamp >= :since
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> countRequestsByHourSince(@Param("since") Instant since);

    @Query(value = """
            SELECT TO_CHAR(DATE_TRUNC('hour', timestamp AT TIME ZONE 'UTC'), 'YYYY-MM-DD"T"HH24') AS hour,
                   COUNT(*) AS cnt
            FROM audit_logs
            WHERE timestamp >= :since
              AND http_status >= 400
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> countErrorsByHourSince(@Param("since") Instant since);

    // ── Per-action breakdown ──────────────────────────────────────────────────

    @Query(value = """
            SELECT action,
                   COUNT(*)                                        AS total,
                   COUNT(*) FILTER (WHERE http_status >= 400)      AS errors
            FROM audit_logs
            GROUP BY action
            ORDER BY total DESC
            """, nativeQuery = true)
    List<Object[]> countByAction();
}