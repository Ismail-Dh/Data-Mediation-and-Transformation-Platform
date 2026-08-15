package com.miniESB.serviceImpl;

import com.miniESB.domain.entity.Payload;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.enums.PayloadStatus;
import com.miniESB.domain.enums.PipelineStatus;
import com.miniESB.dto.monitoring.MonitoringStatsResponse;
import com.miniESB.repository.MonitoringRepository;
import com.miniESB.repository.PayloadRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.service.impl.MonitoringServiceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MonitoringServiceImpl")
class MonitoringServiceImplTest {

    @Mock MonitoringRepository monitoringRepository;
    @Mock PayloadRepository    payloadRepository;
    @Mock PipelineRepository   pipelineRepository;

    @InjectMocks
    MonitoringServiceImpl service;

    // ── helpers ───────────────────────────────────────────────────────────────

    private Pipeline pipeline(PipelineStatus status) {
        Pipeline p = new Pipeline();
        p.setStatus(status);
        return p;
    }

    private Payload payload(PayloadStatus status) {
        Payload p = new Payload();
        p.setStatus(status);
        return p;
    }

    // ── KPIs ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("KPIs globaux")
    class GlobalKpis {

        @Test
        @DisplayName("error rate calculé correctement quand totalRequests > 0")
        void errorRate_calculated() {
            when(monitoringRepository.countAllRequests()).thenReturn(200L);
            when(monitoringRepository.countErrors()).thenReturn(10L);
            when(monitoringRepository.avgDurationMs()).thenReturn(42.0);
            when(pipelineRepository.count()).thenReturn(0L);
            when(pipelineRepository.findAll()).thenReturn(List.of());
            when(payloadRepository.findAll()).thenReturn(List.of());
            when(monitoringRepository.countRequestsByHourSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
            when(monitoringRepository.countErrorsByHourSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
            when(monitoringRepository.countByAction()).thenReturn(List.of());
            when(monitoringRepository.countDistinctUsersSince(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
            when(monitoringRepository.maxDurationMs()).thenReturn(null);
            when(monitoringRepository.countByRole()).thenReturn(List.of());
            when(monitoringRepository.topErrorCodes()).thenReturn(List.of());
            when(monitoringRepository.lastActivityAt()).thenReturn(null);

            MonitoringStatsResponse result = service.getStats();

            assertThat(result.totalRequests()).isEqualTo(200L);
            assertThat(result.totalErrors()).isEqualTo(10L);
            assertThat(result.errorRatePct()).isEqualTo(5.0);

        }

        @Test
        @DisplayName("error rate = 0.0 quand totalRequests == 0 (pas de division par zéro)")
        void errorRate_zero_when_no_requests() {
            when(monitoringRepository.countAllRequests()).thenReturn(0L);
            when(monitoringRepository.countErrors()).thenReturn(0L);
            when(monitoringRepository.avgDurationMs()).thenReturn(null);
            when(pipelineRepository.count()).thenReturn(0L);
            when(pipelineRepository.findAll()).thenReturn(List.of());
            when(payloadRepository.findAll()).thenReturn(List.of());
            when(monitoringRepository.countRequestsByHourSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
            when(monitoringRepository.countErrorsByHourSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
            when(monitoringRepository.countByAction()).thenReturn(List.of());
            when(monitoringRepository.countDistinctUsersSince(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
            when(monitoringRepository.maxDurationMs()).thenReturn(null);
            when(monitoringRepository.countByRole()).thenReturn(List.of());
            when(monitoringRepository.topErrorCodes()).thenReturn(List.of());
            when(monitoringRepository.lastActivityAt()).thenReturn(null);

            MonitoringStatsResponse result = service.getStats();

            assertThat(result.errorRatePct()).isEqualTo(0.0);
        }
    }

    // ── Pipelines ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Comptage pipelines")
    class PipelineCounting {

        @Test
        @DisplayName("seuls les pipelines CONFIGURED et VALIDATED sont comptés comme configurés")
        void configured_pipelines_count() {
            when(monitoringRepository.countAllRequests()).thenReturn(0L);
            when(monitoringRepository.countErrors()).thenReturn(0L);
            when(monitoringRepository.avgDurationMs()).thenReturn(null);
            when(pipelineRepository.count()).thenReturn(4L);
            when(pipelineRepository.findAll()).thenReturn(List.of(
                    pipeline(PipelineStatus.CONFIGURED),
                    pipeline(PipelineStatus.VALIDATED),
                    pipeline(PipelineStatus.DRAFT),
                    pipeline(PipelineStatus.DRAFT)
            ));
            when(payloadRepository.findAll()).thenReturn(List.of());
            when(monitoringRepository.countRequestsByHourSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
            when(monitoringRepository.countErrorsByHourSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
            when(monitoringRepository.countByAction()).thenReturn(List.of());
            when(monitoringRepository.countDistinctUsersSince(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
            when(monitoringRepository.maxDurationMs()).thenReturn(null);
            when(monitoringRepository.countByRole()).thenReturn(List.of());
            when(monitoringRepository.topErrorCodes()).thenReturn(List.of());
            when(monitoringRepository.lastActivityAt()).thenReturn(null);

            MonitoringStatsResponse result = service.getStats();

            assertThat(result.totalPipelines()).isEqualTo(4L);
            assertThat(result.configuredPipelines()).isEqualTo(2L);
        }
    }

    // ── Payloads ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Comptage payloads")
    class PayloadCounting {

        @Test
        @DisplayName("payloads SENT et FAILED comptés séparément")
        void payload_sent_and_failed() {
            when(monitoringRepository.countAllRequests()).thenReturn(0L);
            when(monitoringRepository.countErrors()).thenReturn(0L);
            when(monitoringRepository.avgDurationMs()).thenReturn(null);
            when(pipelineRepository.count()).thenReturn(0L);
            when(pipelineRepository.findAll()).thenReturn(List.of());
            when(payloadRepository.findAll()).thenReturn(List.of(
                    payload(PayloadStatus.SENT),
                    payload(PayloadStatus.SENT),
                    payload(PayloadStatus.FAILED),
                    payload(PayloadStatus.RECEIVED)
            ));
            when(monitoringRepository.countRequestsByHourSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
            when(monitoringRepository.countErrorsByHourSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
            when(monitoringRepository.countByAction()).thenReturn(List.of());
            when(monitoringRepository.countDistinctUsersSince(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
            when(monitoringRepository.maxDurationMs()).thenReturn(null);
            when(monitoringRepository.countByRole()).thenReturn(List.of());
            when(monitoringRepository.topErrorCodes()).thenReturn(List.of());
            when(monitoringRepository.lastActivityAt()).thenReturn(null);

            MonitoringStatsResponse result = service.getStats();

            assertThat(result.totalPayloads()).isEqualTo(4L);
            assertThat(result.successfulPayloads()).isEqualTo(2L);
            assertThat(result.failedPayloads()).isEqualTo(1L);
        }
    }

    // ── Time-series ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Time-series requestsByHour / errorsByHour")
    class TimeSeries {

        @Test
        @DisplayName("rows mappés correctement en HourlyBucket")
        void hourly_buckets_mapped() {
            when(monitoringRepository.countAllRequests()).thenReturn(0L);
            when(monitoringRepository.countErrors()).thenReturn(0L);
            when(monitoringRepository.avgDurationMs()).thenReturn(null);
            when(pipelineRepository.count()).thenReturn(0L);
            when(pipelineRepository.findAll()).thenReturn(List.of());
            when(payloadRepository.findAll()).thenReturn(List.of());
            when(monitoringRepository.countRequestsByHourSince(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(List.<Object[]>of(new Object[]{"2025-01-01T10:00", 5L}));

            when(monitoringRepository.countErrorsByHourSince(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(List.<Object[]>of(new Object[]{"2025-01-01T10:00", 1L}));
            when(monitoringRepository.countByAction()).thenReturn(List.of());
            when(monitoringRepository.countDistinctUsersSince(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
            when(monitoringRepository.maxDurationMs()).thenReturn(null);
            when(monitoringRepository.countByRole()).thenReturn(List.of());
            when(monitoringRepository.topErrorCodes()).thenReturn(List.of());
            when(monitoringRepository.lastActivityAt()).thenReturn(null);

            MonitoringStatsResponse result = service.getStats();

            assertThat(result.requestsByHour()).hasSize(1);
            assertThat(result.requestsByHour().get(0).hour()).isEqualTo("2025-01-01T10:00");
            assertThat(result.requestsByHour().get(0).count()).isEqualTo(5L);
            assertThat(result.errorsByHour().get(0).count()).isEqualTo(1L);
        }

        @Test
        @DisplayName("null dans row[1] → toLong retourne 0")
        void toLong_null_value() {
            when(monitoringRepository.countAllRequests()).thenReturn(0L);
            when(monitoringRepository.countErrors()).thenReturn(0L);
            when(monitoringRepository.avgDurationMs()).thenReturn(null);
            when(pipelineRepository.count()).thenReturn(0L);
            when(pipelineRepository.findAll()).thenReturn(List.of());
            when(payloadRepository.findAll()).thenReturn(List.of());
            when(monitoringRepository.countRequestsByHourSince(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(List.<Object[]>of(new Object[]{"2025-01-01T10:00", null}));

            when(monitoringRepository.countErrorsByHourSince(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(List.of());
            when(monitoringRepository.countByAction()).thenReturn(List.of());
            when(monitoringRepository.countDistinctUsersSince(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
            when(monitoringRepository.maxDurationMs()).thenReturn(null);
            when(monitoringRepository.countByRole()).thenReturn(List.of());
            when(monitoringRepository.topErrorCodes()).thenReturn(List.of());
            when(monitoringRepository.lastActivityAt()).thenReturn(null);

            MonitoringStatsResponse result = service.getStats();

            assertThat(result.requestsByHour().get(0).count()).isEqualTo(0L);
        }
    }

    // ── ActionStat ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Per-action breakdown")
    class ActionStats {

        @Test
        @DisplayName("rows mappés correctement en ActionStat")
        void action_stats_mapped() {
            when(monitoringRepository.countAllRequests()).thenReturn(0L);
            when(monitoringRepository.countErrors()).thenReturn(0L);
            when(monitoringRepository.avgDurationMs()).thenReturn(null);
            when(pipelineRepository.count()).thenReturn(0L);
            when(pipelineRepository.findAll()).thenReturn(List.of());
            when(payloadRepository.findAll()).thenReturn(List.of());
            when(monitoringRepository.countRequestsByHourSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
            when(monitoringRepository.countErrorsByHourSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
            when(monitoringRepository.countByAction())
                    .thenReturn(List.<Object[]>of(new Object[]{"VALIDATE", 10L, 2L}));
            when(monitoringRepository.countDistinctUsersSince(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
            when(monitoringRepository.maxDurationMs()).thenReturn(null);
            when(monitoringRepository.countByRole()).thenReturn(List.of());
            when(monitoringRepository.topErrorCodes()).thenReturn(List.of());
            when(monitoringRepository.lastActivityAt()).thenReturn(null);
            MonitoringStatsResponse result = service.getStats();

            assertThat(result.requestsByAction()).hasSize(1);
            assertThat(result.requestsByAction().get(0).action()).isEqualTo("VALIDATE");
            assertThat(result.requestsByAction().get(0).total()).isEqualTo(10L);
            assertThat(result.requestsByAction().get(0).errors()).isEqualTo(2L);
        }
    }

    // ── Real platform KPIs ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("KPIs plateforme réels (audit_logs uniquement)")
    class PlatformKpis {

        @Test
        @DisplayName("activeUsers24h, maxDurationMs, requestsByRole et topErrors correctement mappés")
        void platform_kpis_mapped() {
            when(monitoringRepository.countAllRequests()).thenReturn(0L);
            when(monitoringRepository.countErrors()).thenReturn(0L);
            when(monitoringRepository.avgDurationMs()).thenReturn(null);
            when(pipelineRepository.count()).thenReturn(0L);
            when(pipelineRepository.findAll()).thenReturn(List.of());
            when(payloadRepository.findAll()).thenReturn(List.of());
            when(monitoringRepository.countRequestsByHourSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
            when(monitoringRepository.countErrorsByHourSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
            when(monitoringRepository.countByAction()).thenReturn(List.of());
            when(monitoringRepository.countDistinctUsersSince(org.mockito.ArgumentMatchers.any())).thenReturn(7L);
            when(monitoringRepository.maxDurationMs()).thenReturn(980L);
            when(monitoringRepository.countByRole())
                    .thenReturn(List.<Object[]>of(new Object[]{"ROLE_ADMIN", 30L}, new Object[]{"ROLE_DEVELOPER", 70L}));
            when(monitoringRepository.topErrorCodes())
                    .thenReturn(List.<Object[]>of(new Object[]{"VALIDATION_ERROR", 5L}));
            java.time.Instant lastActivity = java.time.Instant.parse("2026-08-10T09:00:00Z");
            when(monitoringRepository.lastActivityAt()).thenReturn(lastActivity);

            MonitoringStatsResponse result = service.getStats();

            assertThat(result.activeUsers24h()).isEqualTo(7L);
            assertThat(result.maxDurationMs()).isEqualTo(980L);
            assertThat(result.requestsByRole()).hasSize(2);
            assertThat(result.requestsByRole().get(0).role()).isEqualTo("ROLE_ADMIN");
            assertThat(result.requestsByRole().get(0).count()).isEqualTo(30L);
            assertThat(result.topErrors()).hasSize(1);
            assertThat(result.topErrors().get(0).code()).isEqualTo("VALIDATION_ERROR");
            assertThat(result.topErrors().get(0).count()).isEqualTo(5L);
            assertThat(result.lastActivityAt()).isEqualTo(lastActivity);
        }

        @Test
        @DisplayName("maxDurationMs = null quand aucune durée n'a encore été capturée")
        void maxDurationMs_null_when_none_captured() {
            when(monitoringRepository.countAllRequests()).thenReturn(0L);
            when(monitoringRepository.countErrors()).thenReturn(0L);
            when(monitoringRepository.avgDurationMs()).thenReturn(null);
            when(pipelineRepository.count()).thenReturn(0L);
            when(pipelineRepository.findAll()).thenReturn(List.of());
            when(payloadRepository.findAll()).thenReturn(List.of());
            when(monitoringRepository.countRequestsByHourSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
            when(monitoringRepository.countErrorsByHourSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
            when(monitoringRepository.countByAction()).thenReturn(List.of());
            when(monitoringRepository.countDistinctUsersSince(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
            when(monitoringRepository.maxDurationMs()).thenReturn(null);
            when(monitoringRepository.countByRole()).thenReturn(List.of());
            when(monitoringRepository.topErrorCodes()).thenReturn(List.of());
            when(monitoringRepository.lastActivityAt()).thenReturn(null);

            MonitoringStatsResponse result = service.getStats();

            assertThat(result.maxDurationMs()).isNull();
            assertThat(result.requestsByRole()).isEmpty();
            assertThat(result.topErrors()).isEmpty();
        }
    }
}