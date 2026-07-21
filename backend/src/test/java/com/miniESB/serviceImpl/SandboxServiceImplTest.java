package com.miniESB.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.*;
import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.MappingType;
import com.miniESB.domain.enums.PayloadStatus;
import com.miniESB.domain.enums.PipelineStatus;
import com.miniESB.dto.mapping.MappingResultResponse;
import com.miniESB.dto.sandbox.SandboxRequest;
import com.miniESB.dto.sandbox.SandboxResponse;
import com.miniESB.exception.FieldViolation;
import com.miniESB.exception.PayloadValidationException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.*;
import com.miniESB.service.impl.SandboxServiceImpl;
import com.miniESB.service.BusinessValidatorService;
import com.miniESB.service.MappingService;
import com.miniESB.service.StructuralValidatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link SandboxServiceImpl}.
 *
 * <p>Note : {@code saveLogAsync} est {@code @Async} mais, en test unitaire pur
 * (sans contexte Spring), il est appelé de façon synchrone comme une méthode
 * normale — pas de proxy AOP. On peut donc vérifier directement les
 * interactions avec {@code sandboxLogRepository} après l'appel à {@code run()}.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SandboxServiceImpl")
class SandboxServiceImplTest {

    @Mock private PipelineRepository pipelineRepository;
    @Mock private PayloadRepository payloadRepository;
    @Mock private PipelineFieldRepository pipelineFieldRepository;
    @Mock private ValidationRuleRepository validationRuleRepository;
    @Mock private MappingRuleRepository mappingRuleRepository;
    @Mock private StructuralValidatorService structuralValidatorService;
    @Mock private BusinessValidatorService businessValidatorService;
    @Mock private MappingService mappingService;
    @Mock private SandboxLogRepository sandboxLogRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private SandboxServiceImpl service;

    private Pipeline pipeline;
    private SandboxRequest request;

    @BeforeEach
    void setUp() {
        pipeline = Pipeline.builder()
                .id(1L)
                .status(PipelineStatus.VALIDATED)
                .build();

        request = new SandboxRequest("{\"name\":\"test\"}", "json");

        // Le premier save() de Payload doit renvoyer un payload avec id (simulate persistence)
        lenient().when(payloadRepository.save(any(Payload.class))).thenAnswer(inv -> {
            Payload p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(100L);
            }
            return p;
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Pipeline introuvable
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Pipeline not found")
    class PipelineNotFound {

        @Test
        @DisplayName("throws ResourceNotFoundException when pipeline does not exist")
        void run_unknownPipeline_throwsResourceNotFound() {
            when(pipelineRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.run(99L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verifyNoInteractions(payloadRepository);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Niveau 1 — Validation structurelle
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Structural validation (level 1)")
    class StructuralValidation {

        @Test
        @DisplayName("is skipped with informational message when pipeline has no fields defined")
        void run_noFieldsDefined_skipsStructuralValidation() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of());
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());
            when(mappingService.applyMappingToPayload(eq(1L), any(Long.class)))
                    .thenReturn(new MappingResultResponse(1L, Map.of("a", 1), Map.of("b", 1)));

            SandboxResponse response = service.run(1L, request);

            assertThat(response.validationMessage()).isEqualTo("No schema defined — structural validation skipped");
            verifyNoInteractions(structuralValidatorService);
        }

        @Test
        @DisplayName("fails the payload and short-circuits before rules/mapping when structural validation fails")
        void run_structuralValidationFails_shortCircuits() {
            PipelineField field = mock(PipelineField.class);
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of(field));

            FieldViolation violation = new FieldViolation("email", "MISSING_FIELD", "email is required");
            PayloadValidationException pve = new PayloadValidationException(List.of(violation));
            doThrow(pve).when(structuralValidatorService).validate(eq(request.rawContent()), anyList());

            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());

            SandboxResponse response = service.run(1L, request);

            assertThat(response.validationPassed()).isFalse();
            assertThat(response.mappingApplied()).isFalse();
            assertThat(response.violations()).containsExactly(violation);
            assertThat(response.validationMessage()).contains("email is required");

            // Payload marqué FAILED et sauvegardé
            verify(payloadRepository, times(2)).save(any(Payload.class));

            // Ne doit JAMAIS atteindre les règles métier ni le mapping
            verifyNoInteractions(businessValidatorService);
            verifyNoInteractions(mappingService);
        }

        @Test
        @DisplayName("logs the sandbox run with failureStep=STRUCTURAL")
        void run_structuralValidationFails_logsWithStructuralFailureStep() {
            PipelineField field = mock(PipelineField.class);
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of(field));
            doThrow(new PayloadValidationException(List.of(
                    new FieldViolation("f", "MISSING_FIELD", "missing"))))
                    .when(structuralValidatorService).validate(anyString(), anyList());
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());

            service.run(1L, request);

            verify(sandboxLogRepository).save(argThat(logEntry ->
                    "STRUCTURAL".equals(logEntry.getFailureStep())
                            && !logEntry.isValidationPassed()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Niveau 2 — Règles métier
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Business rules validation (level 2)")
    class BusinessRulesValidation {

        @Test
        @DisplayName("is skipped when no active validation rules exist")
        void run_noActiveRules_skipsBusinessValidation() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of());
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());
            when(mappingService.applyMappingToPayload(eq(1L), any(Long.class)))
                    .thenReturn(new MappingResultResponse(1L, Map.of(), Map.of()));

            service.run(1L, request);

            verifyNoInteractions(businessValidatorService);
        }

        @Test
        @DisplayName("fails the payload and short-circuits before mapping when a business rule fails")
        void run_businessValidationFails_shortCircuitsBeforeMapping() {
            ValidationRule rule = mock(ValidationRule.class);
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of());
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(1L)).thenReturn(List.of(rule));

            FieldViolation violation = new FieldViolation("age", "TYPE_MISMATCH", "age must be numeric");
            doThrow(new PayloadValidationException(List.of(violation)))
                    .when(businessValidatorService).validate(eq(request.rawContent()), anyList());
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());

            SandboxResponse response = service.run(1L, request);

            assertThat(response.validationPassed()).isFalse();
            assertThat(response.violations()).containsExactly(violation);
            verifyNoInteractions(mappingService);
        }

        @Test
        @DisplayName("logs the sandbox run with failureStep=RULES")
        void run_businessValidationFails_logsWithRulesFailureStep() {
            ValidationRule rule = mock(ValidationRule.class);
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of());
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(1L)).thenReturn(List.of(rule));
            doThrow(new PayloadValidationException(List.of(
                    new FieldViolation("f", "TYPE_MISMATCH", "bad type"))))
                    .when(businessValidatorService).validate(anyString(), anyList());
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());

            service.run(1L, request);

            verify(sandboxLogRepository).save(argThat(logEntry ->
                    "RULES".equals(logEntry.getFailureStep())));
        }

        @Test
        @DisplayName("runs structural validation before business rules (order matters)")
        void run_structuralFailsBeforeRulesEvaluated_businessValidatorNeverCalled() {
            PipelineField field = mock(PipelineField.class);
            ValidationRule rule = mock(ValidationRule.class);
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of(field));
            doThrow(new PayloadValidationException(List.of(
                    new FieldViolation("f", "MISSING_FIELD", "missing"))))
                    .when(structuralValidatorService).validate(anyString(), anyList());
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());

            service.run(1L, request);

            // Les règles métier ne doivent même pas être lues/appelées
            verifyNoInteractions(businessValidatorService);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Happy path — validation + mapping
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("returns MAPPED status with validationPassed and mappingApplied true")
        void run_validPayload_returnsSuccessfulMappedResponse() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of());
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());

            Map<String, Object> original = Map.of("firstName", "Aya");
            Map<String, Object> mapped = Map.of("first_name", "Aya");
            when(mappingService.applyMappingToPayload(eq(1L), eq(100L)))
                    .thenReturn(new MappingResultResponse(1L, original, mapped));

            SandboxResponse response = service.run(1L, request);

            assertThat(response.validationPassed()).isTrue();
            assertThat(response.mappingApplied()).isTrue();
            assertThat(response.payloadStatus()).isEqualTo(PayloadStatus.MAPPED);
            assertThat(response.originalPayload()).isEqualTo(original);
            assertThat(response.mappedPayload()).isEqualTo(mapped);
            assertThat(response.violations()).isEmpty();
        }

        @Test
        @DisplayName("includes an active mapping rule summary with resolved values")
        void run_withActiveMappingRule_includesMappingSummary() {
            MappingRule rule = mock(MappingRule.class);
            when(rule.getMappingType()).thenReturn(MappingType.FIELD_PLACEMENT);
            when(rule.getSourceField()).thenReturn("firstName");
            when(rule.getTargetField()).thenReturn("first_name");
            when(rule.getExpression()).thenReturn(null);

            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of());
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of(rule));

            Map<String, Object> original = Map.of("firstName", "Aya");
            Map<String, Object> mapped = Map.of("first_name", "Aya");
            when(mappingService.applyMappingToPayload(eq(1L), eq(100L)))
                    .thenReturn(new MappingResultResponse(1L, original, mapped));

            SandboxResponse response = service.run(1L, request);

            assertThat(response.mappingSummary()).hasSize(1);
        }

        @Test
        @DisplayName("moves payload status to VALIDATED before invoking mapping")
        void run_validPayload_setsValidatedStatusBeforeMapping() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of());
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());
            when(mappingService.applyMappingToPayload(eq(1L), any(Long.class)))
                    .thenReturn(new MappingResultResponse(1L, Map.of(), Map.of()));

            service.run(1L, request);

            verify(payloadRepository, atLeastOnce()).save(argThat(p -> p.getStatus() == PayloadStatus.VALIDATED));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  saveLogAsync — robustesse
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("saveLogAsync robustness")
    class SaveLogAsyncRobustness {

        @Test
        @DisplayName("does not propagate exception when ObjectMapper serialization fails")
        void run_objectMapperThrows_doesNotPropagateException() throws Exception {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of());
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());
            when(mappingService.applyMappingToPayload(eq(1L), any(Long.class)))
                    .thenReturn(new MappingResultResponse(1L, Map.of("a", 1), Map.of("b", 1)));
            when(objectMapper.writeValueAsString(any()))
                    .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {});

            assertThatCode(() -> service.run(1L, request)).doesNotThrowAnyException();

            // Le run() doit quand même rendre une réponse correcte malgré l'échec du log
            verify(sandboxLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("persists a SandboxLog with the correct duration and format on success")
        void run_success_persistsSandboxLogWithFormat() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(pipelineFieldRepository.findAllByPipelineId(1L)).thenReturn(List.of());
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());
            when(mappingRuleRepository.findByPipelineIdAndActiveTrue(1L)).thenReturn(List.of());
            when(mappingService.applyMappingToPayload(eq(1L), any(Long.class)))
                    .thenReturn(new MappingResultResponse(1L, Map.of("a", 1), Map.of("b", 1)));

            service.run(1L, request);

            verify(sandboxLogRepository).save(argThat(logEntry ->
                    "json".equals(logEntry.getInputFormat())
                            && logEntry.getFailureStep() == null
                            && logEntry.getDurationMs() >= 0));
        }
    }
}