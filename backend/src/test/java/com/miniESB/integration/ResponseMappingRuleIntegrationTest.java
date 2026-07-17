package com.miniESB.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRUD des règles de mapping de réponse provider ({@code ResponseMappingRuleController}).
 * Leur effet réel (application sur une vraie réponse HTTP dispatchée) est couvert par
 * {@link ProcessOrchestrationIntegrationTest}, avec WireMock simulant le provider.
 */
class ResponseMappingRuleIntegrationTest extends AbstractIntegrationTest {

    private Long createPipeline(String devToken) {
        Map<String, Object> createBody = Map.of(
                "name", "Response Rules Pipeline " + uniqueUsername("rrp"),
                "version", "1.0",
                "inputFormat", "JSON",
                "outputFormat", "JSON",
                "providers", List.of());
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines", HttpMethod.POST, new HttpEntity<>(createBody, authHeaders(devToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private Long createProvider(String adminToken) {
        Map<String, Object> body = Map.of(
                "name", "Provider " + uniqueUsername("prov"),
                "endpoint", "http://provider.example.com",
                "protocol", "HTTP",
                "timeout", 3000);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/providers", HttpMethod.POST, new HttpEntity<>(body, authHeaders(adminToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private Map<String, Object> ruleBody(String sourceField, String targetField, String mappingType,
                                          boolean required, Long providerId) {
        Map<String, Object> body = new HashMap<>();
        body.put("sourceField", sourceField);
        body.put("targetField", targetField);
        body.put("mappingType", mappingType);
        body.put("required", required);
        body.put("providerId", providerId);
        return body;
    }

    @Test
    void crudLifecycle_forPipelineWideRule() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipeline(devToken);

        // CREATE — no providerId => applies to every provider of the pipeline
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/response-rules", HttpMethod.POST,
                new HttpEntity<>(ruleBody("status", "orderStatus", "FIELD_PLACEMENT", true, null), authHeaders(devToken)),
                Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long ruleId = ((Number) createResponse.getBody().get("id")).longValue();
        assertThat(createResponse.getBody().get("providerId")).isNull();
        assertThat(createResponse.getBody().get("active")).isEqualTo(true);

        // READ (list)
        ResponseEntity<List> listResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/response-rules", HttpMethod.GET,
                new HttpEntity<>(authHeaders(devToken)), List.class);
        assertThat(listResponse.getBody()).hasSize(1);

        // UPDATE
        ResponseEntity<Map> updateResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/response-rules/" + ruleId, HttpMethod.PUT,
                new HttpEntity<>(ruleBody("status", "finalStatus", "FIELD_PLACEMENT", false, null), authHeaders(devToken)),
                Map.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("targetField")).isEqualTo("finalStatus");
        assertThat(updateResponse.getBody().get("required")).isEqualTo(false);

        // DELETE
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/response-rules/" + ruleId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(devToken)), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List> afterDelete = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/response-rules", HttpMethod.GET,
                new HttpEntity<>(authHeaders(devToken)), List.class);
        assertThat(afterDelete.getBody()).isEmpty();
    }

    @Test
    void rule_canBeScopedToASpecificProvider() {
        String admin = adminToken();
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipeline(devToken);
        Long providerId = createProvider(admin);

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/response-rules", HttpMethod.POST,
                new HttpEntity<>(ruleBody("code", "resultCode", "FIELD_PLACEMENT", true, providerId), authHeaders(devToken)),
                Map.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Number) createResponse.getBody().get("providerId")).longValue()).isEqualTo(providerId);
        assertThat(createResponse.getBody().get("providerName")).isNotNull();
    }

    @Test
    void createRule_referencingUnknownProvider_returnsNotFound() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipeline(devToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/response-rules", HttpMethod.POST,
                new HttpEntity<>(ruleBody("code", "resultCode", "FIELD_PLACEMENT", true, 999999L), authHeaders(devToken)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createRule_referencingUnknownPipeline_returnsNotFound() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines/999999/response-rules", HttpMethod.POST,
                new HttpEntity<>(ruleBody("code", "resultCode", "FIELD_PLACEMENT", true, null), authHeaders(devToken)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anyAuthenticatedUser_canManageAnyPipelinesResponseRules_noOwnershipOrRoleCheckEnforced() {
        // Unlike PipelineController (ownership-checked) and PipelineFieldController
        // (hasRole('DEVELOPER') only), ResponseMappingRuleController has NO @PreAuthorize
        // at all, and the service performs no ownership check either. Any authenticated
        // account — including a DEVELOPER who does not own the pipeline — can manage its
        // response-mapping rules. Documenting the actual behavior; worth tightening.
        String ownerToken = registerAndGetToken(uniqueUsername("owner"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipeline(ownerToken);

        String strangerToken = registerAndGetToken(uniqueUsername("stranger"), "Password123!", "DEVELOPER");
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/response-rules", HttpMethod.POST,
                new HttpEntity<>(ruleBody("status", "orderStatus", "FIELD_PLACEMENT", true, null), authHeaders(strangerToken)),
                Map.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
