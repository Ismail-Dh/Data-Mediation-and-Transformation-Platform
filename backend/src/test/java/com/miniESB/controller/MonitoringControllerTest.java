package com.miniESB.controller;

import com.miniESB.dto.monitoring.MonitoringStatsResponse;
import com.miniESB.service.MonitoringService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link MonitoringController}.
 *
 * <p>Note : {@code @PreAuthorize("hasRole('ADMIN')")} n'est PAS testé ici — ce
 * n'est pas du code applicatif exécuté en test unitaire pur (pas de contexte
 * Spring Security), mais une annotation évaluée par un aspect à l'exécution.
 * La restriction ADMIN devrait plutôt être vérifiée par un test d'intégration
 * (ex: {@code @WebMvcTest} + {@code @WithMockUser}) si elle ne l'est pas déjà.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MonitoringController")
class MonitoringControllerTest {

    @Mock
    private MonitoringService monitoringService;

    @InjectMocks
    private MonitoringController controller;

    @Test
    @DisplayName("returns 200 with the stats returned by MonitoringService")
    void getStats_delegatesAndReturnsResponse() {
        MonitoringStatsResponse expected = mock(MonitoringStatsResponse.class);
        when(monitoringService.getStats()).thenReturn(expected);

        ResponseEntity<MonitoringStatsResponse> response = controller.getStats();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("calls MonitoringService.getStats() exactly once")
    void getStats_callsServiceExactlyOnce() {
        when(monitoringService.getStats()).thenReturn(mock(MonitoringStatsResponse.class));

        controller.getStats();

        verify(monitoringService, times(1)).getStats();
        verifyNoMoreInteractions(monitoringService);
    }

    @Test
    @DisplayName("propagates the exception when the service call fails (no local error handling)")
    void getStats_serviceThrows_propagatesException() {
        when(monitoringService.getStats()).thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> controller.getStats())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB unavailable");
    }
}