package com.miniESB.controller;


import com.miniESB.dto.auditlog.AuditLogResponse;
import com.miniESB.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;


import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// =============================================================================
// AuditLogController
// =============================================================================

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogController — unit tests")
class AuditLogControllerTest {

    @Mock  private AuditLogService auditLogService;
    @InjectMocks private AuditLogController auditLogController;

    private final AuditLogResponse sampleLog = new AuditLogResponse(
            1L, "admin", "ADMIN", "CREATE", "Pipeline",
            "42L", "details", 201, null, null, Instant.now(),null);

    @Nested @DisplayName("getAllLogs()")
    class GetAllLogs {

        @Test @DisplayName("returns 200 with list of logs")
        void getAllLogs_returns200() {
            when(auditLogService.getAllLogs("admin", "ADMIN", "CREATE", 201))
                    .thenReturn(List.of(sampleLog));

            ResponseEntity<List<AuditLogResponse>> response =
                    auditLogController.getAllLogs("admin", "ADMIN", "CREATE", 201);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).performedBy()).isEqualTo("admin");
        }

        @Test @DisplayName("returns 200 with empty list when no logs match")
        void getAllLogs_emptyList() {
            when(auditLogService.getAllLogs(null, null, null, null)).thenReturn(List.of());

            ResponseEntity<List<AuditLogResponse>> response =
                    auditLogController.getAllLogs(null, null, null, null);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested @DisplayName("getMyLogs()")
    class GetMyLogs {

        @Test @DisplayName("returns 200 with logs of authenticated user")
        void getMyLogs_returns200() {
            Authentication auth = mock(Authentication.class);
            when(auth.getName()).thenReturn("dev1");
            when(auditLogService.getMyLogs("dev1")).thenReturn(List.of(sampleLog));

            ResponseEntity<List<AuditLogResponse>> response =
                    auditLogController.getMyLogs(auth);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(1);
            verify(auditLogService).getMyLogs("dev1");
        }

        @Test @DisplayName("returns 200 with empty list when user has no logs")
        void getMyLogs_empty() {
            Authentication auth = mock(Authentication.class);
            when(auth.getName()).thenReturn("dev1");
            when(auditLogService.getMyLogs("dev1")).thenReturn(List.of());

            ResponseEntity<List<AuditLogResponse>> response =
                    auditLogController.getMyLogs(auth);

            assertThat(response.getBody()).isEmpty();
        }
    }
}