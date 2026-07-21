package com.miniESB.controller;

import com.miniESB.domain.entity.Payload;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.SandboxLog;
import com.miniESB.dto.sandbox.SandboxLogResponse;
import com.miniESB.dto.sandbox.SandboxRequest;
import com.miniESB.dto.sandbox.SandboxResponse;
import com.miniESB.repository.SandboxLogRepository;
import com.miniESB.service.SandboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link SandboxController}.
 *
 * <p><strong>Note sur {@code getLogs()}</strong> : {@link SandboxLogResponse} n'a
 * pas été fourni, donc on n'inspecte pas ses accesseurs directement. À la place,
 * on vérifie que le mapping {@code SandboxLog → SandboxLogResponse} sollicite bien
 * chaque getter de l'entité source (mockée) — preuve indirecte que le mapping
 * couvre tous les champs, sans dépendre des noms d'accesseurs du DTO.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SandboxController")
class SandboxControllerTest {

    @Mock
    private SandboxService sandboxService;
    @Mock
    private SandboxLogRepository sandboxLogRepository;

    @InjectMocks
    private SandboxController controller;

    private static final Long PIPELINE_ID = 1L;

    // ══════════════════════════════════════════════════════════════════════════
    //  POST /run
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("run")
    class Run {

        @Test
        @DisplayName("delegates to sandboxService.run and returns its response")
        void run_delegatesAndReturnsResponse() {
            SandboxRequest request = new SandboxRequest("{\"name\":\"Aya\"}", "json");
            SandboxResponse expected = new SandboxResponse(
                    PIPELINE_ID, 100L, true, "Validation passed",
                    true, java.util.Map.of("name", "Aya"), java.util.Map.of("full_name", "Aya"),
                    com.miniESB.domain.enums.PayloadStatus.MAPPED,
                    com.miniESB.domain.enums.PipelineStatus.VALIDATED,
                    List.of(), List.of());
            when(sandboxService.run(PIPELINE_ID, request)).thenReturn(expected);

            ResponseEntity<SandboxResponse> response = controller.run(PIPELINE_ID, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(expected);
        }

        @Test
        @DisplayName("propagates the exception when the service call fails")
        void run_serviceThrows_propagatesException() {
            SandboxRequest request = new SandboxRequest("{}", "json");
            when(sandboxService.run(PIPELINE_ID, request))
                    .thenThrow(new com.miniESB.exception.ResourceNotFoundException("Pipeline not found"));

            assertThatThrownBy(() -> controller.run(PIPELINE_ID, request))
                    .isInstanceOf(com.miniESB.exception.ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /logs
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getLogs")
    class GetLogs {

        private SandboxLog mockLogWithPayload() {
            Pipeline pipeline = mock(Pipeline.class);
            when(pipeline.getId()).thenReturn(PIPELINE_ID);
            Payload payload = mock(Payload.class);
            when(payload.getId()).thenReturn(100L);

            SandboxLog log = mock(SandboxLog.class);
            when(log.getId()).thenReturn(1L);
            when(log.getPipeline()).thenReturn(pipeline);
            when(log.getPayload()).thenReturn(payload);
            when(log.isValidationPassed()).thenReturn(true);
            when(log.isMappingApplied()).thenReturn(true);
            when(log.getValidationMessage()).thenReturn("Validation passed");
            when(log.getDurationMs()).thenReturn(120L);
            when(log.getExecutedAt()).thenReturn(LocalDateTime.now());
            when(log.getInputFormat()).thenReturn("json");
            when(log.getRawContent()).thenReturn("{\"name\":\"Aya\"}");
            when(log.getFailureStep()).thenReturn(null);
            when(log.getViolations()).thenReturn(null);
            when(log.getOriginalPayload()).thenReturn("{\"name\":\"Aya\"}");
            when(log.getMappedPayload()).thenReturn("{\"full_name\":\"Aya\"}");
            when(log.getMappingSummary()).thenReturn("[]");
            return log;
        }

        @Test
        @DisplayName("requests the repository with the correct pipelineId and pagination")
        void getLogs_callsRepositoryWithCorrectPageable() {
            when(sandboxLogRepository.findByPipelineIdOrderByExecutedAtDesc(eq(PIPELINE_ID), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            controller.getLogs(PIPELINE_ID, 2, 5);

            verify(sandboxLogRepository).findByPipelineIdOrderByExecutedAtDesc(
                    eq(PIPELINE_ID), eq(PageRequest.of(2, 5)));
        }

        @Test
        @DisplayName("returns 200 with an empty page when there is no history")
        void getLogs_noHistory_returnsEmptyPage() {
            when(sandboxLogRepository.findByPipelineIdOrderByExecutedAtDesc(eq(PIPELINE_ID), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            ResponseEntity<Page<SandboxLogResponse>> response = controller.getLogs(PIPELINE_ID, 0, 10);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getContent()).isEmpty();
        }

        @Test
        @DisplayName("maps every field of SandboxLog into the response (payload present)")
        void getLogs_entryWithPayload_mapsEveryField() {
            SandboxLog log = mockLogWithPayload();
            when(sandboxLogRepository.findByPipelineIdOrderByExecutedAtDesc(eq(PIPELINE_ID), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(log)));

            ResponseEntity<Page<SandboxLogResponse>> response = controller.getLogs(PIPELINE_ID, 0, 10);

            assertThat(response.getBody().getContent()).hasSize(1);

            // Preuve indirecte : chaque getter de l'entité source a bien été lu par le mapping
            verify(log).getId();
            verify(log).getPipeline();
            verify(log, atLeastOnce()).getPayload();
            verify(log).isValidationPassed();
            verify(log).isMappingApplied();
            verify(log).getValidationMessage();
            verify(log).getDurationMs();
            verify(log).getExecutedAt();
            verify(log).getInputFormat();
            verify(log).getRawContent();
            verify(log).getFailureStep();
            verify(log).getViolations();
            verify(log).getOriginalPayload();
            verify(log).getMappedPayload();
            verify(log).getMappingSummary();
        }

        @Test
        @DisplayName("does not call getId() on payload when the log has no associated payload (null-safe)")
        void getLogs_entryWithoutPayload_isNullSafe() {
            SandboxLog log = mock(SandboxLog.class);
            Pipeline pipeline = mock(Pipeline.class);
            when(pipeline.getId()).thenReturn(PIPELINE_ID);
            when(log.getPipeline()).thenReturn(pipeline);
            when(log.getPayload()).thenReturn(null);

            when(sandboxLogRepository.findByPipelineIdOrderByExecutedAtDesc(eq(PIPELINE_ID), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(log)));

            assertThatCode(() -> controller.getLogs(PIPELINE_ID, 0, 10)).doesNotThrowAnyException();

            verify(log).getPayload();
            // Aucune Payload mockée fournie → si le contrôleur appelait getId() dessus sans le null-check,
            // ça lèverait une NullPointerException, pas juste un échec de vérification Mockito.
        }

        @Test
        @DisplayName("preserves total element count and page metadata from the repository")
        void getLogs_preservesPageMetadata() {
            SandboxLog log = mockLogWithPayload();
            Page<SandboxLog> repoPage = new PageImpl<>(List.of(log), PageRequest.of(1, 10), 25);
            when(sandboxLogRepository.findByPipelineIdOrderByExecutedAtDesc(eq(PIPELINE_ID), any(Pageable.class)))
                    .thenReturn(repoPage);

            ResponseEntity<Page<SandboxLogResponse>> response = controller.getLogs(PIPELINE_ID, 1, 10);

            assertThat(response.getBody().getTotalElements()).isEqualTo(25);
            assertThat(response.getBody().getNumber()).isEqualTo(1);
        }
    }
}