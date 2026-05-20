package com.miniESB.serviceImpl;


import com.miniESB.domain.entity.AuditLog;
import com.miniESB.dto.auditlog.AuditEntry;
import com.miniESB.dto.auditlog.AuditLogResponse;
import com.miniESB.repository.AuditLogRepository;
import com.miniESB.service.impl.AuditLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogServiceImpl")
class AuditLogServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    private AuditLog sampleLog;

    @BeforeEach
    void setUp() {
        sampleLog = AuditLog.builder()
                .id(1L)
                .performedBy("admin")
                .performedByRole("ADMIN")
                .action("CREATE")
                .targetEntity("Pipeline")
                .targetId(String.valueOf(42L))
                .details("some details")
                .httpStatus(201)
                .errorMessage(null)
                .errorCode(null)
                .timestamp(Instant.now())
                .build();
    }

    // =========================================================================
    // save()
    // =========================================================================

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("persists a new AuditLog built from the entry")
        void save_persistsAuditLog() {
            AuditEntry entry = new AuditEntry(
                    "admin", "ADMIN", "CREATE", "Pipeline",
                    "42L", "some details", 201, null,null, null);

            auditLogService.save(entry);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getPerformedBy()).isEqualTo("admin");
            assertThat(saved.getPerformedByRole()).isEqualTo("ADMIN");
            assertThat(saved.getAction()).isEqualTo("CREATE");
            assertThat(saved.getTargetEntity()).isEqualTo("Pipeline");
            assertThat(saved.getTargetId()).isEqualTo("42L");
            assertThat(saved.getHttpStatus()).isEqualTo(201);
            assertThat(saved.getTimestamp()).isNotNull();
        }
    }

    // =========================================================================
    // getAllLogs()
    // =========================================================================

    @Nested
    @DisplayName("getAllLogs()")
    class GetAllLogs {

        @Test
        @DisplayName("returns mapped responses for all matching logs")
        void getAllLogs_returnsMappedList() {
            when(auditLogRepository.findByFilters("admin", "ADMIN", "CREATE", 201))
                    .thenReturn(List.of(sampleLog));

            List<AuditLogResponse> result = auditLogService.getAllLogs("admin", "ADMIN", "CREATE", 201);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).performedBy()).isEqualTo("admin");
            assertThat(result.get(0).action()).isEqualTo("CREATE");
        }

        @Test
        @DisplayName("returns empty list when no logs match filters")
        void getAllLogs_returnsEmptyList() {
            when(auditLogRepository.findByFilters(any(), any(), any(), any()))
                    .thenReturn(List.of());

            List<AuditLogResponse> result = auditLogService.getAllLogs(null, null, null, null);

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // getMyLogs()
    // =========================================================================

    @Nested
    @DisplayName("getMyLogs()")
    class GetMyLogs {

        @Test
        @DisplayName("calls repository with username only and returns mapped list")
        void getMyLogs_filtersOnlyByUsername() {
            when(auditLogRepository.findByFilters("dev1", null, null, null))
                    .thenReturn(List.of(sampleLog));

            List<AuditLogResponse> result = auditLogService.getMyLogs("dev1");

            assertThat(result).hasSize(1);
            verify(auditLogRepository).findByFilters("dev1", null, null, null);
        }
    }

    // =========================================================================
    // purgeOldLogs()
    // =========================================================================

    @Nested
    @DisplayName("purgeOldLogs()")
    class PurgeOldLogs {

        @Test
        @DisplayName("deletes logs older than 90 days")
        void purgeOldLogs_callsDeleteWithCorrectInstant() {
            Instant before = Instant.now();
            auditLogService.purgeOldLogs();
            Instant after = Instant.now();

            ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
            verify(auditLogRepository).deleteByTimestampBefore(captor.capture());

            Instant limit = captor.getValue();
            // The limit should be roughly 90 days ago
            assertThat(limit).isBefore(before);
            assertThat(limit).isAfter(before.minusSeconds(90 * 24 * 3600 + 5));
        }
    }
}
