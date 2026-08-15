package com.miniESB.repository;

import com.miniESB.domain.entity.AuditLog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Dedicated repository for monitoring/KPI aggregation queries.
 * Keeps monitoring concerns separate from the generic AuditLogRepository.
 */
@Repository
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = true)

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

    // ── Extra platform KPIs ─────────────────────────────────────────────────────

    /**
     * Distinct users who performed at least one action since :since.
     * Used for the "active users" KPI (real platform activity, not pipeline data).
     */
    @Query("SELECT COUNT(DISTINCT a.performedBy) FROM AuditLog a WHERE a.timestamp >= :since")
    long countDistinctUsersSince(@Param("since") Instant since);

    /**
     * Slowest recorded request — null while no duration_ms has been captured yet.
     */
    @Query("SELECT MAX(a.durationMs) FROM AuditLog a WHERE a.durationMs IS NOT NULL")
    Long maxDurationMs();

    /**
     * Requests grouped by the caller's role (ADMIN / DEVELOPER / …).
     */
    @Query(value = """
            SELECT COALESCE(performed_by_role, 'UNKNOWN') AS role,
                   COUNT(*)                                AS cnt
            FROM audit_logs
            GROUP BY 1
            ORDER BY cnt DESC
            """, nativeQuery = true)
    List<Object[]> countByRole();

    /**
     * Most frequent error codes across the whole platform (top 5).
     */
    @Query(value = """
            SELECT COALESCE(error_code, 'UNKNOWN') AS code,
                   COUNT(*)                          AS cnt
            FROM audit_logs
            WHERE http_status >= 400
            GROUP BY 1
            ORDER BY cnt DESC
            LIMIT 5
            """, nativeQuery = true)
    List<Object[]> topErrorCodes();

    /**
     * Timestamp of the most recent audit log entry, all-time.
     * Lets the UI explain an empty 24h window ("last activity was X ago")
     * instead of a bare, ambiguous "no data".
     */
    @Query("SELECT MAX(a.timestamp) FROM AuditLog a")
    Instant lastActivityAt();
}