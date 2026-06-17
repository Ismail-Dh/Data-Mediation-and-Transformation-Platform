package com.miniESB.service.impl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.miniESB.domain.enums.PayloadStatus;
import com.miniESB.domain.enums.PipelineStatus;
import com.miniESB.dto.monitoring.MonitoringStatsResponse;
import com.miniESB.dto.monitoring.MonitoringStatsResponse.ActionStat;
import com.miniESB.dto.monitoring.MonitoringStatsResponse.HourlyBucket;
import com.miniESB.repository.MonitoringRepository;
import com.miniESB.repository.PayloadRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@Service
@RequiredArgsConstructor
public class MonitoringServiceImpl implements MonitoringService {

    private final MonitoringRepository monitoringRepository;
    private final PayloadRepository    payloadRepository;
    private final PipelineRepository   pipelineRepository;

    @Override
    @Transactional(readOnly = true)
    public MonitoringStatsResponse getStats() {

        // ── Global KPIs ───────────────────────────────────────────────────────
        long   totalRequests = monitoringRepository.countAllRequests();
        long   totalErrors   = monitoringRepository.countErrors();
        double errorRate     = totalRequests == 0 ? 0.0
                : Math.round((totalErrors * 100.0 / totalRequests) * 10.0) / 10.0;
        Double avgDuration   = monitoringRepository.avgDurationMs();

        // ── Pipelines ─────────────────────────────────────────────────────────
        long totalPipelines      = pipelineRepository.count();
        long configuredPipelines = pipelineRepository.findAll().stream()
                .filter(p -> p.getStatus() == PipelineStatus.CONFIGURED
                        || p.getStatus() == PipelineStatus.VALIDATED)
                .count();

        // ── Payloads ──────────────────────────────────────────────────────────
        List<com.miniESB.domain.entity.Payload> allPayloads = payloadRepository.findAll();
        long totalPayloads      = allPayloads.size();
        long successfulPayloads = allPayloads.stream()
                .filter(p -> p.getStatus() == PayloadStatus.SENT).count();
        long failedPayloads     = allPayloads.stream()
                .filter(p -> p.getStatus() == PayloadStatus.FAILED).count();

        // ── Time-series last 24 h ─────────────────────────────────────────────
        Instant since24h = Instant.now().minus(24, ChronoUnit.HOURS);

        List<HourlyBucket> requestsByHour = monitoringRepository
                .countRequestsByHourSince(since24h)
                .stream()
                .map(row -> new MonitoringStatsResponse.HourlyBucket((String) row[0], toLong(row[1])))
                .toList();

        List<HourlyBucket> errorsByHour = monitoringRepository
                .countErrorsByHourSince(since24h)
                .stream()
                .map(row -> new MonitoringStatsResponse.HourlyBucket((String) row[0], toLong(row[1])))
                .toList();

        // ── Per-action breakdown ──────────────────────────────────────────────
        List<ActionStat> requestsByAction = monitoringRepository
                .countByAction()
                .stream()
                .map(row -> new MonitoringStatsResponse.ActionStat(
                        (String) row[0],
                        toLong(row[1]),
                        toLong(row[2])))
                .toList();

        return new MonitoringStatsResponse(
                totalRequests,
                totalErrors,
                errorRate,
                avgDuration,
                totalPipelines,
                configuredPipelines,
                totalPayloads,
                successfulPayloads,
                failedPayloads,
                errorsByHour,
                requestsByHour,
                requestsByAction
        );
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }
}