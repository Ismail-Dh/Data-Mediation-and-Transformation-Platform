package com.miniESB.service;

import com.miniESB.dto.monitoring.MonitoringStatsResponse;

public interface MonitoringService {

    /**
     * Aggregates all KPIs from audit_logs, payloads and pipelines tables.
     * Restricted to ADMIN callers (enforced at controller level).
     */
    MonitoringStatsResponse getStats();
}