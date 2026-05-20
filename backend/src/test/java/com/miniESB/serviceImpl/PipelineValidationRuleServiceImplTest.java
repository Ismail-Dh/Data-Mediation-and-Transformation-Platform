package com.miniESB.serviceImpl;

import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.ValidationRule;
import com.miniESB.domain.enums.RuleType;
import com.miniESB.dto.pipelineValidationRule.PipelineValidationRuleRequest;
import com.miniESB.dto.pipelineValidationRule.PipelineValidationRuleResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.repository.ValidationRuleRepository;
import com.miniESB.service.impl.PipelineValidationRuleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineValidationRuleServiceImpl — unit tests")
class PipelineValidationRuleServiceImplTest {

    @Mock private ValidationRuleRepository validationRuleRepository;
    @Mock private PipelineRepository pipelineRepository;

    @InjectMocks
    private PipelineValidationRuleServiceImpl pipelineValidationRuleService;

    private Pipeline pipeline;
    private ValidationRule privateRule;
    private ValidationRule globalRule;

    @BeforeEach
    void setUp() {
        pipeline = new Pipeline();
        pipeline.setId(1L);

        privateRule = ValidationRule.builder()
                .id(100L)
                .fieldName("orderId")
                .ruleType(RuleType.REGEX)
                .pattern("^ORD-\\d+$")
                .description("Order ID format validation")
                .active(true)
                .global(false)
                .pipeline(pipeline)
                .build();

        globalRule = ValidationRule.builder()
                .id(200L)
                .fieldName("email")
                .ruleType(RuleType.REGEX_EMAIL)
                .pattern("^[A-Za-z0-9+_.-]+@(.+)$")
                .description("Global email validation")
                .active(true)
                .global(true)
                .pipeline(null)
                .build();
    }

    // =========================================================================
    // addRule() — Private rule
    // =========================================================================

    @Nested
    @DisplayName("addRule() — private rule creation")
    class AddPrivateRule {

        @Test
        @DisplayName("creates a private rule successfully")
        void addPrivateRule_success() {
            PipelineValidationRuleRequest request = new PipelineValidationRuleRequest(
                    null,
                    "orderId",
                    RuleType.REGEX,
                    "^ORD-\\d+$",
                    "Order validation",
                    true
            );

            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(validationRuleRepository.existsByPipelineIdAndFieldNameAndRuleType(1L, "orderId", RuleType.REGEX))
                    .thenReturn(false);
            when(validationRuleRepository.save(any(ValidationRule.class))).thenReturn(privateRule);

            PipelineValidationRuleResponse result = pipelineValidationRuleService.addRule(1L, request);

            assertThat(result.id()).isEqualTo(100L);
            assertThat(result.fieldName()).isEqualTo("orderId");
            assertThat(result.ruleType()).isEqualTo(RuleType.REGEX);
            assertThat(result.global()).isFalse();

            ArgumentCaptor<ValidationRule> captor = ArgumentCaptor.forClass(ValidationRule.class);
            verify(validationRuleRepository).save(captor.capture());
            assertThat(captor.getValue().isGlobal()).isFalse();
            assertThat(captor.getValue().getPipeline()).isEqualTo(pipeline);
        }

        @Test
        @DisplayName("creates a private rule with NOT_NULL type (pattern optional)")
        void addPrivateRule_notNullType_success() {
            ValidationRule notNullRule = ValidationRule.builder()
                    .id(101L)
                    .fieldName("customerId")
                    .ruleType(RuleType.NOT_NULL)
                    .pattern(null)
                    .description("Customer ID must not be null")
                    .active(true)
                    .global(false)
                    .pipeline(pipeline)
                    .build();

            PipelineValidationRuleRequest request = new PipelineValidationRuleRequest(
                    null,
                    "customerId",
                    RuleType.NOT_NULL,
                    null,
                    "Customer ID must not be null",
                    true
            );

            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(validationRuleRepository.existsByPipelineIdAndFieldNameAndRuleType(1L, "customerId", RuleType.NOT_NULL))
                    .thenReturn(false);
            when(validationRuleRepository.save(any(ValidationRule.class))).thenReturn(notNullRule);

            PipelineValidationRuleResponse result = pipelineValidationRuleService.addRule(1L, request);

            assertThat(result.ruleType()).isEqualTo(RuleType.NOT_NULL);
            assertThat(result.pattern()).isNull();
        }

        @Test
        @DisplayName("throws IllegalArgumentException when duplicate rule exists")
        void addPrivateRule_duplicate_throwsException() {
            PipelineValidationRuleRequest request = new PipelineValidationRuleRequest(
                    null,
                    "orderId",
                    RuleType.REGEX,
                    "^ORD-\\d+$",
                    "Order validation",
                    true
            );

            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(validationRuleRepository.existsByPipelineIdAndFieldNameAndRuleType(1L, "orderId", RuleType.REGEX))
                    .thenReturn(true);

            assertThatThrownBy(() -> pipelineValidationRuleService.addRule(1L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }
    }

    // =========================================================================
    // addRule() — Attach global rule
    // =========================================================================

    @Nested
    @DisplayName("addRule() — attach global rule")
    class AttachGlobalRule {

        @Test
        @DisplayName("attaches a global rule by copying its definition")
        void attachGlobalRule_success() {
            PipelineValidationRuleRequest request = new PipelineValidationRuleRequest(
                    200L,
                    null,
                    null,
                    null,
                    null,
                    true
            );

            ValidationRule copiedRule = ValidationRule.builder()
                    .id(300L)
                    .fieldName("email")
                    .ruleType(RuleType.REGEX_EMAIL)
                    .pattern("^[A-Za-z0-9+_.-]+@(.+)$")
                    .description("Global email validation")
                    .active(true)
                    .global(true)
                    .pipeline(pipeline)
                    .build();

            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(validationRuleRepository.findById(200L)).thenReturn(Optional.of(globalRule));
            when(validationRuleRepository.save(any(ValidationRule.class))).thenReturn(copiedRule);

            PipelineValidationRuleResponse result = pipelineValidationRuleService.addRule(1L, request);

            assertThat(result.id()).isEqualTo(300L);
            assertThat(result.fieldName()).isEqualTo("email");
            assertThat(result.ruleType()).isEqualTo(RuleType.REGEX_EMAIL);
            assertThat(result.global()).isTrue();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when global rule not found")
        void attachGlobalRule_globalRuleNotFound() {
            PipelineValidationRuleRequest request = new PipelineValidationRuleRequest(
                    999L, null, null, null, null, true
            );

            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(validationRuleRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipelineValidationRuleService.addRule(1L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when rule exists but is not global")
        void attachGlobalRule_ruleNotGlobal() {
            PipelineValidationRuleRequest request = new PipelineValidationRuleRequest(
                    100L, null, null, null, null, true
            );

            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(validationRuleRepository.findById(100L)).thenReturn(Optional.of(privateRule));

            assertThatThrownBy(() -> pipelineValidationRuleService.addRule(1L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // getRules()
    // =========================================================================

    @Nested
    @DisplayName("getRules()")
    class GetRules {

        @Test
        @DisplayName("returns all rules for a pipeline")
        void getRules_success() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(validationRuleRepository.findAllByPipelineId(1L)).thenReturn(List.of(privateRule));

            List<PipelineValidationRuleResponse> result = pipelineValidationRuleService.getRules(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(100L);
        }

        @Test
        @DisplayName("returns empty list when pipeline has no rules")
        void getRules_empty() {
            when(pipelineRepository.findById(1L)).thenReturn(Optional.of(pipeline));
            when(validationRuleRepository.findAllByPipelineId(1L)).thenReturn(List.of());

            List<PipelineValidationRuleResponse> result = pipelineValidationRuleService.getRules(1L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when pipeline does not exist")
        void getRules_pipelineNotFound() {
            when(pipelineRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipelineValidationRuleService.getRules(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // updateRule()
    // =========================================================================

    @Nested
    @DisplayName("updateRule()")
    class UpdateRule {

        @Test
        @DisplayName("updates a private rule successfully")
        void updatePrivateRule_success() {
            PipelineValidationRuleRequest request = new PipelineValidationRuleRequest(
                    null,
                    "updatedField",
                    RuleType.REGEX_EMAIL,
                    "updated-pattern@.*",
                    "Updated description",
                    false
            );

            when(validationRuleRepository.findById(100L)).thenReturn(Optional.of(privateRule));
            when(validationRuleRepository.existsByPipelineIdAndFieldNameAndRuleType(1L, "updatedField", RuleType.REGEX_EMAIL))
                    .thenReturn(false);
            when(validationRuleRepository.save(any(ValidationRule.class))).thenReturn(privateRule);

            PipelineValidationRuleResponse result = pipelineValidationRuleService.updateRule(1L, 100L, request);

            assertThat(result.fieldName()).isEqualTo("updatedField");
            assertThat(result.ruleType()).isEqualTo(RuleType.REGEX_EMAIL);
            assertThat(result.pattern()).isEqualTo("updated-pattern@.*");
            assertThat(result.description()).isEqualTo("Updated description");
            assertThat(result.active()).isFalse();
        }

        @Test
        @DisplayName("throws IllegalArgumentException when trying to update a global rule")
        void updateGlobalRule_throwsException() {
            ValidationRule global = ValidationRule.builder()
                    .id(200L)
                    .global(true)
                    .pipeline(pipeline)
                    .build();

            PipelineValidationRuleRequest request = new PipelineValidationRuleRequest(
                    null, "email", RuleType.REGEX_EMAIL, null, null, true
            );

            when(validationRuleRepository.findById(200L)).thenReturn(Optional.of(global));

            assertThatThrownBy(() -> pipelineValidationRuleService.updateRule(1L, 200L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Global rules cannot be edited");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when duplicate after fieldName change")
        void updateRule_duplicateAfterChange_throwsException() {
            PipelineValidationRuleRequest request = new PipelineValidationRuleRequest(
                    null,
                    "existingField",
                    RuleType.REGEX,
                    null,
                    null,
                    true
            );

            when(validationRuleRepository.findById(100L)).thenReturn(Optional.of(privateRule));
            when(validationRuleRepository.existsByPipelineIdAndFieldNameAndRuleType(1L, "existingField", RuleType.REGEX))
                    .thenReturn(true);

            assertThatThrownBy(() -> pipelineValidationRuleService.updateRule(1L, 100L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when rule not found")
        void updateRule_ruleNotFound() {
            PipelineValidationRuleRequest request = new PipelineValidationRuleRequest(
                    null, "field", RuleType.REGEX, null, null, true
            );

            when(validationRuleRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipelineValidationRuleService.updateRule(1L, 999L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // toggleActive()
    // =========================================================================

    @Nested
    @DisplayName("toggleActive()")
    class ToggleActive {

        @Test
        @DisplayName("toggles active flag from true to false")
        void toggleActive_trueToFalse() {
            // Créer un mock de ValidationRule pour pouvoir vérifier les appels
            ValidationRule mockRule = mock(ValidationRule.class);
            when(mockRule.getId()).thenReturn(100L);
            when(mockRule.getPipeline()).thenReturn(pipeline);
            when(mockRule.isActive()).thenReturn(true);

            // Configuration du comportement attendu
            doAnswer(invocation -> {
                when(mockRule.isActive()).thenReturn(false);
                return null;
            }).when(mockRule).setActive(false);

            when(validationRuleRepository.findById(100L)).thenReturn(Optional.of(mockRule));
            when(validationRuleRepository.save(any(ValidationRule.class))).thenReturn(mockRule);

            PipelineValidationRuleResponse result = pipelineValidationRuleService.toggleActive(1L, 100L);

            assertThat(result.active()).isFalse();
            verify(mockRule).setActive(false);
            verify(validationRuleRepository).save(mockRule);
        }

        @Test
        @DisplayName("toggles active flag from false to true")
        void toggleActive_falseToTrue() {
            // Créer un mock de ValidationRule pour pouvoir vérifier les appels
            ValidationRule mockRule = mock(ValidationRule.class);
            when(mockRule.getId()).thenReturn(100L);
            when(mockRule.getPipeline()).thenReturn(pipeline);
            when(mockRule.isActive()).thenReturn(false);

            // Configuration du comportement attendu
            doAnswer(invocation -> {
                when(mockRule.isActive()).thenReturn(true);
                return null;
            }).when(mockRule).setActive(true);

            when(validationRuleRepository.findById(100L)).thenReturn(Optional.of(mockRule));
            when(validationRuleRepository.save(any(ValidationRule.class))).thenReturn(mockRule);

            PipelineValidationRuleResponse result = pipelineValidationRuleService.toggleActive(1L, 100L);

            assertThat(result.active()).isTrue();
            verify(mockRule).setActive(true);
            verify(validationRuleRepository).save(mockRule);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when rule not found")
        void toggleActive_ruleNotFound() {
            when(validationRuleRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipelineValidationRuleService.toggleActive(1L, 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when rule does not belong to pipeline")
        void toggleActive_wrongPipeline() {
            ValidationRule mockRule = mock(ValidationRule.class);
            Pipeline wrongPipeline = new Pipeline();
            wrongPipeline.setId(2L);

            when(mockRule.getPipeline()).thenReturn(wrongPipeline);
            when(validationRuleRepository.findById(100L)).thenReturn(Optional.of(mockRule));

            assertThatThrownBy(() -> pipelineValidationRuleService.toggleActive(1L, 100L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("does not belong");
        }
    }

    // =========================================================================
    // deleteRule()
    // =========================================================================

    @Nested
    @DisplayName("deleteRule()")
    class DeleteRule {

        @Test
        @DisplayName("deletes rule successfully")
        void deleteRule_success() {
            ValidationRule mockRule = mock(ValidationRule.class);
            when(mockRule.getPipeline()).thenReturn(pipeline);
            when(validationRuleRepository.findById(100L)).thenReturn(Optional.of(mockRule));

            pipelineValidationRuleService.deleteRule(1L, 100L);

            verify(validationRuleRepository).delete(mockRule);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when rule not found")
        void deleteRule_ruleNotFound() {
            when(validationRuleRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pipelineValidationRuleService.deleteRule(1L, 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when rule does not belong to pipeline")
        void deleteRule_wrongPipeline() {
            ValidationRule mockRule = mock(ValidationRule.class);
            Pipeline wrongPipeline = new Pipeline();
            wrongPipeline.setId(2L);

            when(mockRule.getPipeline()).thenReturn(wrongPipeline);
            when(validationRuleRepository.findById(100L)).thenReturn(Optional.of(mockRule));

            assertThatThrownBy(() -> pipelineValidationRuleService.deleteRule(1L, 100L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("does not belong");
        }
    }
}