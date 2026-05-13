package com.miniESB.serviceImpl;


import com.miniESB.domain.entity.ValidationRule;
import com.miniESB.domain.enums.RuleType;
import com.miniESB.dto.globalValidationRule.GlobalValidationRuleRequestDTO;
import com.miniESB.dto.globalValidationRule.GlobalValidationRuleResponseDTO;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.exception.RuleInUseException;
import com.miniESB.repository.ValidationRuleRepository;
import com.miniESB.service.impl.GlobalValidationRuleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalValidationRuleServiceImpl")
class GlobalValidationRuleServiceImplTest {

    @Mock
    private ValidationRuleRepository validationRuleRepository;

    @InjectMocks
    private GlobalValidationRuleServiceImpl service;

    private ValidationRule globalRule;
    private GlobalValidationRuleRequestDTO request;

    @BeforeEach
    void setUp() {
        globalRule = ValidationRule.builder()
                .id(1L)
                .fieldName("email")
                .ruleType(RuleType.valueOf("REGEX_EMAIL"))
                .pattern(null)
                .active(true)
                .global(true)
                .description("Email validation")
                .build();

        request = new GlobalValidationRuleRequestDTO(
                "email",RuleType.valueOf("REGEX_EMAIL"), null, true, "Email validation");
    }

    // =========================================================================
    // createRule()
    // =========================================================================

    @Nested
    @DisplayName("createRule()")
    class CreateRule {

        @Test
        @DisplayName("saves a rule with global=true and returns response")
        void createRule_savesAndReturnsDto() {
            when(validationRuleRepository.save(any(ValidationRule.class))).thenReturn(globalRule);

            GlobalValidationRuleResponseDTO result = service.createRule(request);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.fieldName()).isEqualTo("email");
            assertThat(result.global()).isTrue();
            verify(validationRuleRepository).save(any(ValidationRule.class));
        }
    }

    // =========================================================================
    // getAllRules()
    // =========================================================================

    @Nested
    @DisplayName("getAllRules()")
    class GetAllRules {

        @Test
        @DisplayName("returns all global rules")
        void getAllRules_returnsAllGlobal() {
            when(validationRuleRepository.findAllByGlobalTrue()).thenReturn(List.of(globalRule));

            List<GlobalValidationRuleResponseDTO> result = service.getAllRules();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).fieldName()).isEqualTo("email");
        }

        @Test
        @DisplayName("returns empty list when no global rules exist")
        void getAllRules_emptyList() {
            when(validationRuleRepository.findAllByGlobalTrue()).thenReturn(List.of());

            assertThat(service.getAllRules()).isEmpty();
        }
    }

    // =========================================================================
    // getActiveRules()
    // =========================================================================

    @Nested
    @DisplayName("getActiveRules()")
    class GetActiveRules {

        @Test
        @DisplayName("returns only active global rules")
        void getActiveRules_returnsActive() {
            when(validationRuleRepository.findAllByGlobalTrueAndActiveTrue()).thenReturn(List.of(globalRule));

            List<GlobalValidationRuleResponseDTO> result = service.getActiveRules();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).active()).isTrue();
        }
    }

    // =========================================================================
    // getRuleById()
    // =========================================================================

    @Nested
    @DisplayName("getRuleById()")
    class GetRuleById {

        @Test
        @DisplayName("returns DTO when rule exists and is global")
        void getRuleById_found() {
            when(validationRuleRepository.findById(1L)).thenReturn(Optional.of(globalRule));

            GlobalValidationRuleResponseDTO result = service.getRuleById(1L);

            assertThat(result.id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when rule not found")
        void getRuleById_notFound() {
            when(validationRuleRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getRuleById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when rule exists but is not global")
        void getRuleById_notGlobal() {
            ValidationRule nonGlobal = ValidationRule.builder().id(2L).global(false).build();
            when(validationRuleRepository.findById(2L)).thenReturn(Optional.of(nonGlobal));

            assertThatThrownBy(() -> service.getRuleById(2L))
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
        @DisplayName("updates fields and saves")
        void updateRule_updatesAndSaves() {
            when(validationRuleRepository.findById(1L)).thenReturn(Optional.of(globalRule));
            when(validationRuleRepository.save(any())).thenReturn(globalRule);

            GlobalValidationRuleResponseDTO result = service.updateRule(1L, request);

            assertThat(result).isNotNull();
            verify(validationRuleRepository).save(globalRule);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when rule not found")
        void updateRule_notFound() {
            when(validationRuleRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateRule(99L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // deactivateRule()
    // =========================================================================

    @Nested
    @DisplayName("deactivateRule()")
    class DeactivateRule {

        @Test
        @DisplayName("sets active=false and saves")
        void deactivateRule_setsActiveFalse() {
            when(validationRuleRepository.findById(1L)).thenReturn(Optional.of(globalRule));

            service.deactivateRule(1L);

            assertThat(globalRule.isActive()).isFalse();
            verify(validationRuleRepository).save(globalRule);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when rule not found")
        void deactivateRule_notFound() {
            when(validationRuleRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deactivateRule(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // deleteRule()
    // =========================================================================

    @Nested
    @DisplayName("deleteRule()")
    class DeleteRule {

        @Test
        @DisplayName("deletes rule when it exists and is not in use")
        void deleteRule_success() {
            when(validationRuleRepository.findById(1L)).thenReturn(Optional.of(globalRule));
            when(validationRuleRepository.isGlobalRuleUsedByPipeline(1L)).thenReturn(false);

            service.deleteRule(1L);

            verify(validationRuleRepository).deleteById(1L);
        }

        @Test
        @DisplayName("throws RuleInUseException when rule is referenced by a pipeline")
        void deleteRule_ruleInUse() {
            when(validationRuleRepository.findById(1L)).thenReturn(Optional.of(globalRule));
            when(validationRuleRepository.isGlobalRuleUsedByPipeline(1L)).thenReturn(true);

            assertThatThrownBy(() -> service.deleteRule(1L))
                    .isInstanceOf(RuleInUseException.class);

            verify(validationRuleRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when rule not found")
        void deleteRule_notFound() {
            when(validationRuleRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteRule(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}