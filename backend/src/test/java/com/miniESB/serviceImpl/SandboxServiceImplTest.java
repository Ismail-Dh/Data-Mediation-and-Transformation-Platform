package com.miniESB.serviceImpl;

import com.miniESB.domain.entity.Payload;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.PipelineField;
import com.miniESB.domain.entity.ValidationRule;
import com.miniESB.domain.enums.PayloadStatus;
import com.miniESB.domain.enums.PipelineStatus;
import com.miniESB.dto.mapping.MappingResultResponse;
import com.miniESB.dto.sandbox.SandboxRequest;
import com.miniESB.dto.sandbox.SandboxResponse;
import com.miniESB.exception.FieldViolation;
import com.miniESB.exception.PayloadValidationException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.PayloadRepository;
import com.miniESB.repository.PipelineFieldRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.repository.ValidationRuleRepository;
import com.miniESB.service.BusinessValidatorService;
import com.miniESB.service.MappingService;
import com.miniESB.service.StructuralValidatorService;
import com.miniESB.service.impl.SandboxServiceImpl;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SandboxServiceImpl")
class SandboxServiceImplTest {

    @Mock PipelineRepository         pipelineRepository;
    @Mock PayloadRepository          payloadRepository;
    @Mock PipelineFieldRepository    pipelineFieldRepository;
    @Mock ValidationRuleRepository   validationRuleRepository;
    @Mock StructuralValidatorService structuralValidatorService;
    @Mock BusinessValidatorService   businessValidatorService;
    @Mock MappingService             mappingService;

    @InjectMocks
    SandboxServiceImpl service;

    private static final Long   PIPELINE_ID = 1L;
    private static final Long   PAYLOAD_ID  = 10L;
    private static final String RAW_CONTENT = "{\"name\":\"Aya\"}";

    private SandboxRequest request() {
        return new SandboxRequest(RAW_CONTENT, "JSON");
    }

    private Pipeline pipeline() {
        Pipeline p = new Pipeline();
        p.setId(PIPELINE_ID);
        p.setStatus(PipelineStatus.VALIDATED);
        return p;
    }

    private Payload savedPayload() {
        Payload p = new Payload();
        p.setId(PAYLOAD_ID);
        p.setStatus(PayloadStatus.RECEIVED);
        return p;
    }

    private MappingResultResponse mappingResult() {
    return new MappingResultResponse(
            PIPELINE_ID,
            Map.of("name", "Aya"),
            Map.of("fullName", "Aya")
    );
}

    // ── Pipeline not found ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pipeline introuvable")
    class PipelineNotFound {

        @Test
        @DisplayName("lance ResourceNotFoundException si pipeline inexistant")
        void throws_when_pipeline_not_found() {
            when(pipelineRepository.findById(PIPELINE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.run(PIPELINE_ID, request()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(String.valueOf(PIPELINE_ID));
        }
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cas nominal — validation + mapping OK")
    class HappyPath {

        @Test
        @DisplayName("retourne validationPassed=true et mappingApplied=true")
        void full_success() {
            when(pipelineRepository.findById(PIPELINE_ID)).thenReturn(Optional.of(pipeline()));
            when(payloadRepository.save(any())).thenReturn(savedPayload());
            when(pipelineFieldRepository.findAllByPipelineId(PIPELINE_ID))
                    .thenReturn(List.of(new PipelineField()));
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(PIPELINE_ID))
                    .thenReturn(List.of(new ValidationRule()));
            when(mappingService.applyMappingToPayload(PIPELINE_ID, PAYLOAD_ID))
                    .thenReturn(mappingResult());

            SandboxResponse result = service.run(PIPELINE_ID, request());

            assertThat(result.validationPassed()).isTrue();
            assertThat(result.mappingApplied()).isTrue();
            assertThat(result.payloadStatus()).isEqualTo(PayloadStatus.MAPPED);
            assertThat(result.pipelineStatus()).isEqualTo(PipelineStatus.VALIDATED);
            assertThat(result.originalPayload()).containsKey("name");
            assertThat(result.mappedPayload()).containsKey("fullName");
        }

        @Test
        @DisplayName("payload sauvegardé 3 fois : RECEIVED → VALIDATED → (mapping)")
        void payload_saved_multiple_times() {
            when(pipelineRepository.findById(PIPELINE_ID)).thenReturn(Optional.of(pipeline()));
            when(payloadRepository.save(any())).thenReturn(savedPayload());
            when(pipelineFieldRepository.findAllByPipelineId(PIPELINE_ID))
                    .thenReturn(List.of(new PipelineField()));
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(PIPELINE_ID))
                    .thenReturn(List.of());
            when(mappingService.applyMappingToPayload(PIPELINE_ID, PAYLOAD_ID))
                    .thenReturn(mappingResult());

            service.run(PIPELINE_ID, request());

            // save appelé au moins 2 fois : création + status VALIDATED
            verify(payloadRepository, atLeast(2)).save(any());
        }
    }

    // ── No schema ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pas de schema défini")
    class NoSchema {

        @Test
        @DisplayName("validation structurelle skippée, message adapté")
        void structural_skipped_when_no_fields() {
            when(pipelineRepository.findById(PIPELINE_ID)).thenReturn(Optional.of(pipeline()));
            when(payloadRepository.save(any())).thenReturn(savedPayload());
            when(pipelineFieldRepository.findAllByPipelineId(PIPELINE_ID))
                    .thenReturn(List.of());
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(PIPELINE_ID))
                    .thenReturn(List.of());
            when(mappingService.applyMappingToPayload(PIPELINE_ID, PAYLOAD_ID))
                    .thenReturn(mappingResult());

            SandboxResponse result = service.run(PIPELINE_ID, request());

            assertThat(result.validationPassed()).isTrue();
            assertThat(result.validationMessage()).contains("skipped");
            verifyNoInteractions(structuralValidatorService);
        }
    }

    // ── Structural failure ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Échec validation structurelle (niveau 1)")
    class StructuralFailure {

        @Test
        @DisplayName("retourne validationPassed=false et payload FAILED")
        void structural_fail_returns_fail_response() {
            PayloadValidationException pve = new PayloadValidationException(
                    List.of(new FieldViolation("name", "MISSING_FIELD", "champ requis")));

            when(pipelineRepository.findById(PIPELINE_ID)).thenReturn(Optional.of(pipeline()));
            when(payloadRepository.save(any())).thenReturn(savedPayload());
            when(pipelineFieldRepository.findAllByPipelineId(PIPELINE_ID))
                    .thenReturn(List.of(new PipelineField()));
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(PIPELINE_ID))
                    .thenReturn(List.of());
            doThrow(pve).when(structuralValidatorService).validate(any(), any());

            SandboxResponse result = service.run(PIPELINE_ID, request());

            assertThat(result.validationPassed()).isFalse();
            assertThat(result.mappingApplied()).isFalse();
            assertThat(result.payloadStatus()).isEqualTo(PayloadStatus.FAILED);
            assertThat(result.validationMessage()).contains("violation");
            verifyNoInteractions(businessValidatorService, mappingService);
        }
    }

    // ── Business failure ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Échec validation métier (niveau 2)")
    class BusinessFailure {

        @Test
        @DisplayName("retourne validationPassed=false après niveau 1 OK")
        void business_fail_returns_fail_response() {
            PayloadValidationException pve = new PayloadValidationException(
                    List.of(new FieldViolation("age", "INVALID_FORMAT", "doit être >= 18")));

            when(pipelineRepository.findById(PIPELINE_ID)).thenReturn(Optional.of(pipeline()));
            when(payloadRepository.save(any())).thenReturn(savedPayload());
            when(pipelineFieldRepository.findAllByPipelineId(PIPELINE_ID))
                    .thenReturn(List.of(new PipelineField()));
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(PIPELINE_ID))
                    .thenReturn(List.of(new ValidationRule()));
            doThrow(pve).when(businessValidatorService).validate(any(), any());

            SandboxResponse result = service.run(PIPELINE_ID, request());

            assertThat(result.validationPassed()).isFalse();
            assertThat(result.mappingApplied()).isFalse();
            assertThat(result.payloadStatus()).isEqualTo(PayloadStatus.FAILED);
            assertThat(result.validationMessage()).contains("violation");
            verifyNoInteractions(mappingService);
        }

        @Test
        @DisplayName("business validator skippé si aucune règle active")
        void business_skipped_when_no_rules() {
            when(pipelineRepository.findById(PIPELINE_ID)).thenReturn(Optional.of(pipeline()));
            when(payloadRepository.save(any())).thenReturn(savedPayload());
            when(pipelineFieldRepository.findAllByPipelineId(PIPELINE_ID))
                    .thenReturn(List.of(new PipelineField()));
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(PIPELINE_ID))
                    .thenReturn(List.of());
            when(mappingService.applyMappingToPayload(PIPELINE_ID, PAYLOAD_ID))
                    .thenReturn(mappingResult());

            service.run(PIPELINE_ID, request());

            verifyNoInteractions(businessValidatorService);
        }
    }
}