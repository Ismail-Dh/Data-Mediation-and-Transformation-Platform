package com.miniESB.controller;

import com.miniESB.domain.enums.RuleType;
import com.miniESB.dto.pipelineValidationRule.PipelineValidationRuleRequest;
import com.miniESB.dto.pipelineValidationRule.PipelineValidationRuleResponse;

import com.miniESB.exception.ResourceNotFoundException;

import com.miniESB.service.PipelineValidationRuleService;
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
@DisplayName("PipelineValidationRuleController — unit tests")
class PipelineValidationRuleControllerTest {

    @Mock  private PipelineValidationRuleService pipelineValidationRuleService;
    @InjectMocks private PipelineValidationRuleController controller;

    private final PipelineValidationRuleResponse privateRuleDto =
            new PipelineValidationRuleResponse(
                    10L, "email", RuleType.valueOf("REGEX_EMAIL"), null, "Email check", true, false, 1L);

    private final PipelineValidationRuleResponse globalRuleDto =
            new PipelineValidationRuleResponse(
                    20L, "orderId", RuleType.valueOf("NOT_NULL"), null, "Order not null", true, true, 1L);

    @Nested
    @DisplayName("addRule()")
    class AddRule {

        @Test
        @DisplayName("returns 201 when creating a private rule")
        void addRule_private_returns201() {
            PipelineValidationRuleRequest req = new PipelineValidationRuleRequest(
                    null, "email", RuleType.valueOf("REGEX_EMAIL"), null, "Email check", true);
            when(pipelineValidationRuleService.addRule(1L, req)).thenReturn(privateRuleDto);

            ResponseEntity<PipelineValidationRuleResponse> response =
                    controller.addRule(1L, req);

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(response.getBody().fieldName()).isEqualTo("email");
            assertThat(response.getBody().global()).isFalse();
        }

        @Test
        @DisplayName("returns 201 when attaching a global rule")
        void addRule_global_returns201() {
            PipelineValidationRuleRequest req = new PipelineValidationRuleRequest(
                    20L, null, null, null, null, true);
            when(pipelineValidationRuleService.addRule(1L, req)).thenReturn(globalRuleDto);

            ResponseEntity<PipelineValidationRuleResponse> response =
                    controller.addRule(1L, req);

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(response.getBody().global()).isTrue();
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when global rule not found")
        void addRule_globalRuleNotFound() {
            PipelineValidationRuleRequest req = new PipelineValidationRuleRequest(
                    99L, null, null, null, null, true);
            when(pipelineValidationRuleService.addRule(1L, req))
                    .thenThrow(new ResourceNotFoundException("Global rule not found"));

            assertThatThrownBy(() -> controller.addRule(1L, req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("propagates IllegalArgumentException on duplicate private rule")
        void addRule_duplicatePrivate() {
            PipelineValidationRuleRequest req = new PipelineValidationRuleRequest(
                    null, "email", RuleType.valueOf("REGEX_EMAIL"), null, null, true);
            when(pipelineValidationRuleService.addRule(1L, req))
                    .thenThrow(new IllegalArgumentException("A rule of type REGEX_EMAIL already exists"));

            assertThatThrownBy(() -> controller.addRule(1L, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("REGEX_EMAIL");
        }
    }

    @Nested
    @DisplayName("getRules()")
    class GetRules {

        @Test
        @DisplayName("returns 200 with all rules of a pipeline")
        void getRules_returns200() {
            when(pipelineValidationRuleService.getRules(1L))
                    .thenReturn(List.of(privateRuleDto, globalRuleDto));

            ResponseEntity<List<PipelineValidationRuleResponse>> response =
                    controller.getRules(1L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(2);
        }

        @Test
        @DisplayName("returns 200 with empty list when no rules exist")
        void getRules_empty() {
            when(pipelineValidationRuleService.getRules(1L)).thenReturn(List.of());

            assertThat(controller.getRules(1L).getBody()).isEmpty();
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when pipeline not found")
        void getRules_pipelineNotFound() {
            when(pipelineValidationRuleService.getRules(99L))
                    .thenThrow(new ResourceNotFoundException("Pipeline not found"));

            assertThatThrownBy(() -> controller.getRules(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateRule()")
    class UpdateRule {

        @Test
        @DisplayName("returns 200 with updated private rule")
        void updateRule_returns200() {
            PipelineValidationRuleRequest req = new PipelineValidationRuleRequest(
                    null, "email", RuleType.valueOf("REGEX_EMAIL"), ".*@.*", "Updated", false);
            when(pipelineValidationRuleService.updateRule(1L, 10L, req))
                    .thenReturn(privateRuleDto);

            ResponseEntity<PipelineValidationRuleResponse> response =
                    controller.updateRule(1L, 10L, req);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }

        @Test
        @DisplayName("propagates IllegalArgumentException when trying to edit a global rule")
        void updateRule_globalRule_throws() {
            PipelineValidationRuleRequest req = new PipelineValidationRuleRequest(
                    null, "orderId", RuleType.valueOf("NOT_NULL"), null, null, true);
            when(pipelineValidationRuleService.updateRule(1L, 20L, req))
                    .thenThrow(new IllegalArgumentException("Global rules cannot be edited here"));

            assertThatThrownBy(() -> controller.updateRule(1L, 20L, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Global rules");
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when rule not found")
        void updateRule_notFound() {
            PipelineValidationRuleRequest req = new PipelineValidationRuleRequest(
                    null, "email", RuleType.valueOf("REGEX_EMAIL"), null, null, true);
            when(pipelineValidationRuleService.updateRule(1L, 99L, req))
                    .thenThrow(new ResourceNotFoundException("Validation rule not found"));

            assertThatThrownBy(() -> controller.updateRule(1L, 99L, req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("toggleActive()")
    class ToggleActive {

        @Test
        @DisplayName("returns 200 with toggled rule")
        void toggleActive_returns200() {
            PipelineValidationRuleResponse toggled = new PipelineValidationRuleResponse(
                    10L, "email", RuleType.valueOf("REGEX_EMAIL"), null, "Email check", false, false, 1L);
            when(pipelineValidationRuleService.toggleActive(1L, 10L)).thenReturn(toggled);

            ResponseEntity<PipelineValidationRuleResponse> response =
                    controller.toggleActive(1L, 10L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().active()).isFalse();
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when rule not found")
        void toggleActive_notFound() {
            when(pipelineValidationRuleService.toggleActive(1L, 99L))
                    .thenThrow(new ResourceNotFoundException("Validation rule not found"));

            assertThatThrownBy(() -> controller.toggleActive(1L, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteRule()")
    class DeleteRule {

        @Test
        @DisplayName("returns 204 on successful delete")
        void deleteRule_returns204() {
            doNothing().when(pipelineValidationRuleService).deleteRule(1L, 10L);

            ResponseEntity<Void> response = controller.deleteRule(1L, 10L);

            assertThat(response.getStatusCode().value()).isEqualTo(204);
            verify(pipelineValidationRuleService).deleteRule(1L, 10L);
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when rule not found")
        void deleteRule_notFound() {
            doThrow(new ResourceNotFoundException("Validation rule not found"))
                    .when(pipelineValidationRuleService).deleteRule(1L, 99L);

            assertThatThrownBy(() -> controller.deleteRule(1L, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when rule belongs to different pipeline")
        void deleteRule_wrongPipeline() {
            doThrow(new ResourceNotFoundException("Rule does not belong to pipeline"))
                    .when(pipelineValidationRuleService).deleteRule(1L, 20L);

            assertThatThrownBy(() -> controller.deleteRule(1L, 20L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("pipeline");
        }
    }
}
