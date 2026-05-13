package com.miniESB.controller;





import com.miniESB.domain.enums.RuleType;
import com.miniESB.dto.globalValidationRule.GlobalValidationRuleRequestDTO;

import com.miniESB.dto.globalValidationRule.GlobalValidationRuleResponseDTO;


import com.miniESB.exception.ResourceNotFoundException;

import com.miniESB.exception.RuleInUseException;

import com.miniESB.service.*;

import org.junit.jupiter.api.DisplayName;

import org.junit.jupiter.api.Nested;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;

import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.ResponseEntity;



import java.util.List;

import static org.assertj.core.api.Assertions.*;


import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalValidationRuleController — unit tests")
class GlobalValidationRuleControllerTest {

    @Mock  private GlobalValidationRuleService globalValidationRuleService;
    @InjectMocks private GlobalValidationRuleController globalValidationRuleController;

    private final GlobalValidationRuleResponseDTO ruleDto =
            new GlobalValidationRuleResponseDTO(1L, "email", RuleType.valueOf("REGEX_EMAIL"), null, true, true, "desc");
    private final GlobalValidationRuleRequestDTO request =
            new GlobalValidationRuleRequestDTO("email", RuleType.valueOf("REGEX_EMAIL"), null, true, "desc");

    @Nested @DisplayName("createRule()")
    class CreateRule {

        @Test @DisplayName("returns 201 with created rule")
        void createRule_returns201() {
            when(globalValidationRuleService.createRule(request)).thenReturn(ruleDto);

            ResponseEntity<GlobalValidationRuleResponseDTO> response =
                    globalValidationRuleController.createRule(request);

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(response.getBody().id()).isEqualTo(1L);
            assertThat(response.getBody().fieldName()).isEqualTo("email");
        }
    }

    @Nested @DisplayName("getAllRules()")
    class GetAllRules {

        @Test @DisplayName("returns 200 with all rules")
        void getAllRules_returns200() {
            when(globalValidationRuleService.getAllRules()).thenReturn(List.of(ruleDto));

            ResponseEntity<List<GlobalValidationRuleResponseDTO>> response =
                    globalValidationRuleController.getAllRules();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test @DisplayName("returns 200 with empty list when no rules exist")
        void getAllRules_empty() {
            when(globalValidationRuleService.getAllRules()).thenReturn(List.of());

            assertThat(globalValidationRuleController.getAllRules().getBody()).isEmpty();
        }
    }

    @Nested @DisplayName("getRuleById()")
    class GetRuleById {

        @Test @DisplayName("returns 200 when rule found")
        void getRuleById_returns200() {
            when(globalValidationRuleService.getRuleById(1L)).thenReturn(ruleDto);

            ResponseEntity<GlobalValidationRuleResponseDTO> response =
                    globalValidationRuleController.getRuleById(1L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().id()).isEqualTo(1L);
        }

        @Test @DisplayName("propagates ResourceNotFoundException when rule not found")
        void getRuleById_notFound() {
            when(globalValidationRuleService.getRuleById(99L))
                    .thenThrow(new ResourceNotFoundException("not found"));

            assertThatThrownBy(() -> globalValidationRuleController.getRuleById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("updateRule()")
    class UpdateRule {

        @Test @DisplayName("returns 200 with updated rule")
        void updateRule_returns200() {
            when(globalValidationRuleService.updateRule(1L, request)).thenReturn(ruleDto);

            ResponseEntity<GlobalValidationRuleResponseDTO> response =
                    globalValidationRuleController.updateRule(1L, request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }
    }

    @Nested @DisplayName("deactivateRule()")
    class DeactivateRule {

        @Test @DisplayName("returns 204 on successful deactivation")
        void deactivateRule_returns204() {
            doNothing().when(globalValidationRuleService).deactivateRule(1L);

            ResponseEntity<Void> response = globalValidationRuleController.deactivateRule(1L);

            assertThat(response.getStatusCode().value()).isEqualTo(204);
            verify(globalValidationRuleService).deactivateRule(1L);
        }
    }

    @Nested @DisplayName("deleteRule()")
    class DeleteRule {

        @Test @DisplayName("returns 204 on successful delete")
        void deleteRule_returns204() {
            doNothing().when(globalValidationRuleService).deleteRule(1L);

            ResponseEntity<Void> response = globalValidationRuleController.deleteRule(1L);

            assertThat(response.getStatusCode().value()).isEqualTo(204);
        }

        @Test @DisplayName("propagates RuleInUseException when rule is referenced by pipeline")
        void deleteRule_ruleInUse() {
            doThrow(new RuleInUseException(1L)).when(globalValidationRuleService).deleteRule(1L);

            assertThatThrownBy(() -> globalValidationRuleController.deleteRule(1L))
                    .isInstanceOf(RuleInUseException.class);
        }
    }
}
