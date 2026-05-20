package com.miniESB.dto.monitoring;

import java.util.List;

/**
 * Response DTO for GET /api/admin/monitoring/stats
 * All KPIs derived from existing audit_logs + payloads + pipelines tables.
 */
public record MonitoringStatsResponse(

        // ── Global KPIs ────────────────────────────────────────────────────────
        long   totalRequests,      // COUNT(*) audit_logs
        long   totalErrors,        // COUNT where http_status >= 400
        double errorRatePct,       // totalErrors / totalRequests * 100
        Double avgDurationMs,      // null until AuditAspect records duration_ms

        // ── Pipelines ─────────────────────────────────────────────────────────
        long   totalPipelines,
        long   configuredPipelines,   // status = CONFIGURED or VALIDATED (usable)

        // ── Payloads ──────────────────────────────────────────────────────────
        long   totalPayloads,
        long   successfulPayloads,    // status = SENT
        long   failedPayloads,        // status = FAILED

        // ── Time-series (last 24 h, one bucket per hour) ───────────────────────
        List<HourlyBucket> errorsByHour,       // for line chart
        List<HourlyBucket> requestsByHour,     // for bar chart

        // ── Per-action breakdown ───────────────────────────────────────────────
        List<ActionStat> requestsByAction

) {
    public record HourlyBucket(String hour, long count) {}
    public record ActionStat(String action, long total, long errors) {}
}