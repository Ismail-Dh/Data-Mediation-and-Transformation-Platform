package com.miniESB.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Moteur de mapping : création/CRUD des {@code MappingRule} d'un pipeline, application
 * réelle des règles à un payload JSON (FIELD_PLACEMENT, CALCULATED_FIELD), et effet de
 * bord sur le statut du pipeline (DRAFT -> CONFIGURED dès qu'une règle est créée), qui
 * débloque ensuite validate/revert.
 */
class MappingRuleIntegrationTest extends AbstractIntegrationTest {

    private Long createPipeline(String devToken) {
        Map<String, Object> createBody = Map.of(
                "name", "Mapping Pipeline " + uniqueUsername("mp"),
                "version", "1.0",
                "inputFormat", "JSON",
                "outputFormat", "JSON",
                "providers", List.of());
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines", HttpMethod.POST, new HttpEntity<>(createBody, authHeaders(devToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private Long createRule(String devToken, Long pipelineId, String sourceField, String targetField,
                             String mappingType, String expression) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("sourceField", sourceField);
        body.put("targetField", targetField);
        body.put("mappingType", mappingType);
        body.put("expression", expression);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/mappings", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(devToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void fieldPlacement_movesAndRenamesFieldInMappedOutput() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipeline(devToken);

        createRule(devToken, pipelineId, "customerName", "client.fullName", "FIELD_PLACEMENT", null);

        Map<String, String> applyBody = Map.of(
                "rawContent", "{\"customerName\":\"Alice\",\"amount\":10}");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/mappings/apply", HttpMethod.POST,
                new HttpEntity<>(applyBody, authHeaders(devToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> mapped = (Map<String, Object>) response.getBody().get("mapped");
        Map<String, Object> client = (Map<String, Object>) mapped.get("client");
        assertThat(client).isNotNull();
        assertThat(client.get("fullName")).isEqualTo("Alice");
        // the source field was relocated, not duplicated
        assertThat(mapped).doesNotContainKey("customerName");
        // untouched fields are preserved
        assertThat(((Number) mapped.get("amount")).intValue()).isEqualTo(10);

        // original payload returned unchanged, for comparison
        Map<String, Object> original = (Map<String, Object>) response.getBody().get("original");
        assertThat(original.get("customerName")).isEqualTo("Alice");
    }

    @SuppressWarnings("unchecked")
    @Test
    void calculatedField_evaluatesArithmeticExpression() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipeline(devToken);

        createRule(devToken, pipelineId, "unused", "total", "CALCULATED_FIELD", "{price} * {quantity}");

        Map<String, String> applyBody = Map.of("rawContent", "{\"price\":10,\"quantity\":5}");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/mappings/apply", HttpMethod.POST,
                new HttpEntity<>(applyBody, authHeaders(devToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> mapped = (Map<String, Object>) response.getBody().get("mapped");
        assertThat(((Number) mapped.get("total")).doubleValue()).isEqualTo(50.0);
    }

    @Test
    void creatingMappingRule_movesPipelineToConfigured_andUnlocksValidateRevert() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipeline(devToken);

        // freshly created pipeline is DRAFT -> validation is refused (state guard)
        ResponseEntity<Map> validateTooEarly = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/validate", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(devToken)), Map.class);
        // NB: PipelineServiceImpl throws IllegalStateException here, which has no dedicated
        // handler in GlobalExceptionHandler and falls back to the generic 500 handler instead
        // of a more appropriate 409/422. Documented as-is; worth adding a specific handler.
        assertThat(validateTooEarly.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // creating a mapping rule flips the pipeline to CONFIGURED as a side effect
        createRule(devToken, pipelineId, "a", "b", "FIELD_PLACEMENT", null);

        ResponseEntity<Map> getAfterRule = restTemplate.exchange(
                "/api/pipelines/" + pipelineId, HttpMethod.GET, new HttpEntity<>(authHeaders(devToken)), Map.class);
        assertThat(getAfterRule.getBody().get("status")).isEqualTo("CONFIGURED");

        // now validation succeeds
        ResponseEntity<Map> validateResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/validate", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(devToken)), Map.class);
        assertThat(validateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(validateResponse.getBody().get("status")).isEqualTo("VALIDATED");

        // and can be reverted back to CONFIGURED
        ResponseEntity<Map> revertResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/revert", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(devToken)), Map.class);
        assertThat(revertResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(revertResponse.getBody().get("status")).isEqualTo("CONFIGURED");
    }

    @Test
    void disablingAndReactivatingRule_affectsActiveRuleListingOnly() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipeline(devToken);

        Long ruleId1 = createRule(devToken, pipelineId, "a", "x", "FIELD_PLACEMENT", null);
        createRule(devToken, pipelineId, "b", "y", "FIELD_PLACEMENT", null);

        // both active initially
        ResponseEntity<List> activeRules = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/mappings", HttpMethod.GET,
                new HttpEntity<>(authHeaders(devToken)), List.class);
        assertThat(activeRules.getBody()).hasSize(2);

        // soft-delete (disable) the first one
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/mappings/" + ruleId1, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(devToken)), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List> activeAfterDisable = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/mappings", HttpMethod.GET,
                new HttpEntity<>(authHeaders(devToken)), List.class);
        assertThat(activeAfterDisable.getBody()).hasSize(1);

        // "all rules" still shows both, the disabled one flagged inactive
        ResponseEntity<List> allRules = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/mappings/all", HttpMethod.GET,
                new HttpEntity<>(authHeaders(devToken)), List.class);
        assertThat(allRules.getBody()).hasSize(2);

        // reactivate it
        ResponseEntity<Map> activateResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/mappings/" + ruleId1 + "/activate", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(devToken)), Map.class);
        assertThat(activateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activateResponse.getBody().get("active")).isEqualTo(true);

        ResponseEntity<List> activeAfterReactivate = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/mappings", HttpMethod.GET,
                new HttpEntity<>(authHeaders(devToken)), List.class);
        assertThat(activeAfterReactivate.getBody()).hasSize(2);
    }

    @Test
    void createRule_onUnknownPipeline_returnsNotFound() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");

        Map<String, Object> body = Map.of(
                "sourceField", "a", "targetField", "b", "mappingType", "FIELD_PLACEMENT");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines/999999/mappings", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(devToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
