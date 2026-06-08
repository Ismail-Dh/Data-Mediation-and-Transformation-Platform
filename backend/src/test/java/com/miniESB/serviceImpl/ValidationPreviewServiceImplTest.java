package com.miniESB.serviceImpl;

import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.PipelineField;
import com.miniESB.domain.entity.ValidationRule;
import com.miniESB.dto.validation.ValidationPreviewRequest;
import com.miniESB.dto.validation.ValidationPreviewResponse;
import com.miniESB.exception.FieldViolation;
import com.miniESB.exception.PayloadValidationException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.PipelineFieldRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.repository.ValidationRuleRepository;
import com.miniESB.service.BusinessValidatorService;
import com.miniESB.service.StructuralValidatorService;
import com.miniESB.service.impl.ValidationPreviewServiceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidationPreviewServiceImpl")
class ValidationPreviewServiceImplTest {

    @Mock PipelineRepository         pipelineRepository;
    @Mock PipelineFieldRepository    pipelineFieldRepository;
    @Mock ValidationRuleRepository   validationRuleRepository;
    @Mock StructuralValidatorService structuralValidatorService;
    @Mock BusinessValidatorService   businessValidatorService;

    @InjectMocks
    ValidationPreviewServiceImpl service;

    private static final Long   PIPELINE_ID  = 1L;
    private static final String RAW_CONTENT  = "{\"name\":\"Aya\"}";

    private ValidationPreviewRequest request() {
    return new ValidationPreviewRequest(RAW_CONTENT, "JSON");
    }

    private PipelineField field() {
        return new PipelineField();
    }

    private ValidationRule rule() {
        return new ValidationRule();
    }

    // ── Pipeline not found ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pipeline introuvable")
    class PipelineNotFound {

        @Test
        @DisplayName("lance ResourceNotFoundException si pipeline inexistant")
        void throws_when_pipeline_not_found() {
            when(pipelineRepository.findById(PIPELINE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.preview(PIPELINE_ID, request()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(String.valueOf(PIPELINE_ID));
        }
    }

    // ── Trivially valid ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Aucun schema ni règle")
    class TriviallyValid {

        @Test
        @DisplayName("retourne valid=true quand fields et rules sont vides")
        void trivially_valid_when_no_schema_no_rules() {
            when(pipelineRepository.findById(PIPELINE_ID))
                    .thenReturn(Optional.of(new Pipeline()));
            when(pipelineFieldRepository.findAllByPipelineId(PIPELINE_ID))
                    .thenReturn(List.of());
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(PIPELINE_ID))
                    .thenReturn(List.of());

            ValidationPreviewResponse result = service.preview(PIPELINE_ID, request());

            assertThat(result.valid()).isTrue();
            assertThat(result.structuralOk()).isTrue();
            assertThat(result.businessOk()).isTrue();
            assertThat(result.violationCount()).isEqualTo(0);
            verifyNoInteractions(structuralValidatorService, businessValidatorService);
        }
    }

    // ── Structural validation ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Validation structurelle (niveau 1)")
    class StructuralValidation {

        @Test
        @DisplayName("retourne structuralOk=true quand le payload est valide")
        void structural_ok() {
            when(pipelineRepository.findById(PIPELINE_ID))
                    .thenReturn(Optional.of(new Pipeline()));
            when(pipelineFieldRepository.findAllByPipelineId(PIPELINE_ID))
                    .thenReturn(List.of(field()));
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(PIPELINE_ID))
                    .thenReturn(List.of());

            ValidationPreviewResponse result = service.preview(PIPELINE_ID, request());

            assertThat(result.structuralOk()).isTrue();
            assertThat(result.valid()).isTrue();
            verify(structuralValidatorService).validate(eq(RAW_CONTENT), any());
        }

        @Test
        @DisplayName("retourne structuralOk=false et arrête quand niveau 1 échoue")
        void structural_fail_stops_processing() {
            FieldViolation violation = new FieldViolation("name", "MISSING_FIELD", "champ requis");

            PayloadValidationException ex = new PayloadValidationException(List.of(violation));

            when(pipelineRepository.findById(PIPELINE_ID))
                    .thenReturn(Optional.of(new Pipeline()));
            when(pipelineFieldRepository.findAllByPipelineId(PIPELINE_ID))
                    .thenReturn(List.of(field()));
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(PIPELINE_ID))
                    .thenReturn(List.of(rule()));
            doThrow(ex).when(structuralValidatorService).validate(any(), any());

            ValidationPreviewResponse result = service.preview(PIPELINE_ID, request());

            assertThat(result.valid()).isFalse();
            assertThat(result.structuralOk()).isFalse();
            assertThat(result.businessOk()).isFalse();
            assertThat(result.violationCount()).isEqualTo(1);
            // business validator ne doit PAS être appelé
            verifyNoInteractions(businessValidatorService);
        }
    }

    // ── Business validation ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Validation métier (niveau 2)")
    class BusinessValidation {

        @Test
        @DisplayName("retourne businessOk=true quand les règles sont respectées")
        void business_ok() {
            when(pipelineRepository.findById(PIPELINE_ID))
                    .thenReturn(Optional.of(new Pipeline()));
            when(pipelineFieldRepository.findAllByPipelineId(PIPELINE_ID))
                    .thenReturn(List.of(field()));
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(PIPELINE_ID))
                    .thenReturn(List.of(rule()));

            ValidationPreviewResponse result = service.preview(PIPELINE_ID, request());

            assertThat(result.valid()).isTrue();
            assertThat(result.structuralOk()).isTrue();
            assertThat(result.businessOk()).isTrue();
            verify(businessValidatorService).validate(eq(RAW_CONTENT), any());
        }

        @Test
        @DisplayName("retourne businessOk=false quand une règle métier est violée")
        void business_fail() {
            FieldViolation violation = new FieldViolation("age", "INVALID_FORMAT", "doit être >= 18");
            PayloadValidationException ex = new PayloadValidationException(List.of(violation));

            when(pipelineRepository.findById(PIPELINE_ID))
                    .thenReturn(Optional.of(new Pipeline()));
            when(pipelineFieldRepository.findAllByPipelineId(PIPELINE_ID))
                    .thenReturn(List.of(field()));
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(PIPELINE_ID))
                    .thenReturn(List.of(rule()));
            doThrow(ex).when(businessValidatorService).validate(any(), any());

            ValidationPreviewResponse result = service.preview(PIPELINE_ID, request());

            assertThat(result.valid()).isFalse();
            assertThat(result.structuralOk()).isTrue(); // niveau 1 a passé
            assertThat(result.businessOk()).isFalse();
            assertThat(result.violationCount()).isEqualTo(1);
            assertThat(result.violations()).hasSize(1);
        }

        @Test
        @DisplayName("business validator non appelé si aucune règle active")
        void business_skipped_when_no_rules() {
            when(pipelineRepository.findById(PIPELINE_ID))
                    .thenReturn(Optional.of(new Pipeline()));
            when(pipelineFieldRepository.findAllByPipelineId(PIPELINE_ID))
                    .thenReturn(List.of(field()));
            when(validationRuleRepository.findAllByPipelineIdAndActiveTrue(PIPELINE_ID))
                    .thenReturn(List.of());

            service.preview(PIPELINE_ID, request());

            verifyNoInteractions(businessValidatorService);
        }
    }
}
