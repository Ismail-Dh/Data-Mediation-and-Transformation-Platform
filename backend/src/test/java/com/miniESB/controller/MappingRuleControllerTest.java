package com.miniESB.controller;

import com.miniESB.dto.mapping.ApplyMappingRequest;
import com.miniESB.dto.mapping.MappingResultResponse;
import com.miniESB.dto.mapping.MappingRuleRequest;
import com.miniESB.dto.mapping.MappingRuleResponse;
import com.miniESB.domain.enums.MappingType;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.service.MappingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MappingRuleController — unit tests")
class MappingRuleControllerTest {

    @Mock private MappingService mappingService;
    @InjectMocks private MappingRuleController mappingRuleController;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private final MappingRuleResponse ruleDto = new MappingRuleResponse(
            1L, "client_id", "id_client", MappingType.FIELD_PLACEMENT, null, true, 1L);

    private final MappingRuleResponse ruleInactiveDto = new MappingRuleResponse(
            1L, "client_id", "id_client", MappingType.FIELD_PLACEMENT, null, false, 1L);

    private final MappingResultResponse resultDto = new MappingResultResponse(
            1L,
            Map.of("client_id", "123"),
            Map.of("id_client", "123")
    );

    // =========================================================================
    // createRule()
    // =========================================================================

    @Nested
    @DisplayName("createRule()")
    class CreateRule {

        @Test
        @DisplayName("returns 201 with created rule")
        void createRule_returns201() {
            MappingRuleRequest req = new MappingRuleRequest(
                    "client_id", "id_client", MappingType.FIELD_PLACEMENT, null);
            when(mappingService.createRule(1L, req)).thenReturn(ruleDto);

            ResponseEntity<MappingRuleResponse> response =
                    mappingRuleController.createRule(1L, req);

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(response.getBody().sourceField()).isEqualTo("client_id");
            assertThat(response.getBody().targetField()).isEqualTo("id_client");
            assertThat(response.getBody().active()).isTrue();
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when pipeline not found")
        void createRule_pipelineNotFound() {
            MappingRuleRequest req = new MappingRuleRequest(
                    "src", "tgt", MappingType.FIELD_PLACEMENT, null);
            when(mappingService.createRule(99L, req))
                    .thenThrow(new ResourceNotFoundException("Pipeline not found"));

            assertThatThrownBy(() -> mappingRuleController.createRule(99L, req))
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
        @DisplayName("returns 200 with active rules only")
        void getRules_returns200() {
            when(mappingService.getRulesByPipeline(1L)).thenReturn(List.of(ruleDto));

            ResponseEntity<List<MappingRuleResponse>> response =
                    mappingRuleController.getRules(1L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).active()).isTrue();
        }

        @Test
        @DisplayName("returns 200 with empty list when no active rules")
        void getRules_emptyList() {
            when(mappingService.getRulesByPipeline(1L)).thenReturn(List.of());

            ResponseEntity<List<MappingRuleResponse>> response =
                    mappingRuleController.getRules(1L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when pipeline not found")
        void getRules_pipelineNotFound() {
            when(mappingService.getRulesByPipeline(99L))
                    .thenThrow(new ResourceNotFoundException("Pipeline not found"));

            assertThatThrownBy(() -> mappingRuleController.getRules(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // getAllRules()
    // =========================================================================

    @Nested
    @DisplayName("getAllRules()")
    class GetAllRules {

        @Test
        @DisplayName("returns 200 with active and inactive rules")
        void getAllRules_returns200() {
            when(mappingService.getAllRulesByPipeline(1L))
                    .thenReturn(List.of(ruleDto, ruleInactiveDto));

            ResponseEntity<List<MappingRuleResponse>> response =
                    mappingRuleController.getAllRules(1L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody().get(0).active()).isTrue();
            assertThat(response.getBody().get(1).active()).isFalse();
        }
    }

    // =========================================================================
    // deleteRule()
    // =========================================================================

    @Nested
    @DisplayName("deleteRule()")
    class DeleteRule {

        @Test
        @DisplayName("returns 204 on successful soft delete")
        void deleteRule_returns204() {
            doNothing().when(mappingService).deleteRule(1L, 1L);

            ResponseEntity<Void> response = mappingRuleController.deleteRule(1L, 1L);

            assertThat(response.getStatusCode().value()).isEqualTo(204);
            verify(mappingService).deleteRule(1L, 1L);
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when rule not found")
        void deleteRule_notFound() {
            doThrow(new ResourceNotFoundException("MappingRule not found"))
                    .when(mappingService).deleteRule(1L, 99L);

            assertThatThrownBy(() -> mappingRuleController.deleteRule(1L, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when rule belongs to another pipeline")
        void deleteRule_wrongPipeline() {
            doThrow(new ResourceNotFoundException("MappingRule does not belong to pipeline"))
                    .when(mappingService).deleteRule(2L, 1L);

            assertThatThrownBy(() -> mappingRuleController.deleteRule(2L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // activateRule()
    // =========================================================================

    @Nested
    @DisplayName("activateRule()")
    class ActivateRule {

        @Test
        @DisplayName("returns 200 with reactivated rule")
        void activateRule_returns200() {
            when(mappingService.activateRule(1L, 1L)).thenReturn(ruleDto);

            ResponseEntity<MappingRuleResponse> response =
                    mappingRuleController.activateRule(1L, 1L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().active()).isTrue();
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when rule not found")
        void activateRule_notFound() {
            when(mappingService.activateRule(1L, 99L))
                    .thenThrow(new ResourceNotFoundException("MappingRule not found"));

            assertThatThrownBy(() -> mappingRuleController.activateRule(1L, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when rule belongs to another pipeline")
        void activateRule_wrongPipeline() {
            when(mappingService.activateRule(2L, 1L))
                    .thenThrow(new ResourceNotFoundException("MappingRule does not belong to pipeline"));

            assertThatThrownBy(() -> mappingRuleController.activateRule(2L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // applyMapping()
    // =========================================================================

    @Nested
    @DisplayName("applyMapping()")
    class ApplyMapping {

        @Test
        @DisplayName("returns 200 with original and mapped payload")
        void applyMapping_returns200() {
            ApplyMappingRequest req = new ApplyMappingRequest("{\"client_id\": \"123\"}");
            when(mappingService.applyMappingToPayload(1L, req.rawContent()))
                    .thenReturn(resultDto);

            ResponseEntity<MappingResultResponse> response =
                    mappingRuleController.applyMapping(1L, req);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().original()).containsKey("client_id");
            assertThat(response.getBody().mapped()).containsKey("id_client");
        }

        @Test
        @DisplayName("propagates IllegalArgumentException when rawContent is invalid JSON")
        void applyMapping_invalidJson() {
            ApplyMappingRequest req = new ApplyMappingRequest("not a json");
            when(mappingService.applyMappingToPayload(1L, req.rawContent()))
                    .thenThrow(new IllegalArgumentException("Invalid JSON content"));

            assertThatThrownBy(() -> mappingRuleController.applyMapping(1L, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid JSON content");
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when pipeline not found")
        void applyMapping_pipelineNotFound() {
            ApplyMappingRequest req = new ApplyMappingRequest("{\"client_id\": \"123\"}");
            when(mappingService.applyMappingToPayload(99L, req.rawContent()))
                    .thenThrow(new ResourceNotFoundException("Pipeline not found"));

            assertThatThrownBy(() -> mappingRuleController.applyMapping(99L, req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}