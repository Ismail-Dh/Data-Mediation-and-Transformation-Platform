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
 * Cycle de vie d'un Pipeline : création par un DEVELOPER, rattachement d'un Provider,
 * contrôle d'accès basé sur la propriété (ownership), et visibilité ADMIN.
 */
class PipelineIntegrationTest extends AbstractIntegrationTest {

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

    @Test
    void developer_canCreateReadUpdateDelete_ownPipeline() {
        String admin = adminToken();
        Long providerId = createProvider(admin);
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");

        // CREATE with an attached provider
        Map<String, Object> createBody = Map.of(
                "name", "Order Pipeline",
                "version", "1.0",
                "inputFormat", "JSON",
                "outputFormat", "JSON",
                "providers", List.of(Map.of("providerId", providerId, "httpMethod", "POST")));

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/pipelines", HttpMethod.POST, new HttpEntity<>(createBody, authHeaders(devToken)), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long pipelineId = ((Number) createResponse.getBody().get("id")).longValue();
        assertThat(createResponse.getBody().get("status")).isEqualTo("DRAFT");
        assertThat((List<?>) createResponse.getBody().get("providers")).hasSize(1);

        // Owner can read
        ResponseEntity<Map> ownerGet = restTemplate.exchange(
                "/api/pipelines/" + pipelineId, HttpMethod.GET, new HttpEntity<>(authHeaders(devToken)), Map.class);
        assertThat(ownerGet.getStatusCode()).isEqualTo(HttpStatus.OK);

        // A different developer cannot read someone else's pipeline
        String otherDevToken = registerAndGetToken(uniqueUsername("dev2"), "Password123!", "DEVELOPER");
        ResponseEntity<Map> forbiddenGet = restTemplate.exchange(
                "/api/pipelines/" + pipelineId, HttpMethod.GET, new HttpEntity<>(authHeaders(otherDevToken)), Map.class);
        assertThat(forbiddenGet.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // ADMIN can read any pipeline
        ResponseEntity<Map> adminGet = restTemplate.exchange(
                "/api/pipelines/" + pipelineId, HttpMethod.GET, new HttpEntity<>(authHeaders(admin)), Map.class);
        assertThat(adminGet.getStatusCode()).isEqualTo(HttpStatus.OK);

        // it shows up in the owner's "my pipelines"
        ResponseEntity<List> myPipelines = restTemplate.exchange(
                "/api/pipelines/my", HttpMethod.GET, new HttpEntity<>(authHeaders(devToken)), List.class);
        assertThat(myPipelines.getBody())
                .extracting(p -> ((Number) ((Map<?, ?>) p).get("id")).longValue())
                .contains(pipelineId);

        // UPDATE (partial) by the owner
        Map<String, Object> updateBody = Map.of("name", "Order Pipeline v2");
        ResponseEntity<Map> updateResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId, HttpMethod.PATCH, new HttpEntity<>(updateBody, authHeaders(devToken)), Map.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("name")).isEqualTo("Order Pipeline v2");
        // providers untouched by this partial update
        assertThat((List<?>) updateResponse.getBody().get("providers")).hasSize(1);

        // a non-owner cannot update it either
        ResponseEntity<Map> forbiddenUpdate = restTemplate.exchange(
                "/api/pipelines/" + pipelineId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("name", "Hijacked"), authHeaders(otherDevToken)), Map.class);
        assertThat(forbiddenUpdate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // DELETE by the owner
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId, HttpMethod.DELETE, new HttpEntity<>(authHeaders(devToken)), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> afterDelete = restTemplate.exchange(
                "/api/pipelines/" + pipelineId, HttpMethod.GET, new HttpEntity<>(authHeaders(devToken)), Map.class);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listAllPipelines_isAdminOnly_andIncludesEveryonesPipelines() {
        String admin = adminToken();
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");

        Map<String, Object> createBody = Map.of(
                "name", "Another Pipeline",
                "version", "1.0",
                "inputFormat", "JSON",
                "outputFormat", "XML",
                "providers", List.of());
        restTemplate.exchange("/api/pipelines", HttpMethod.POST, new HttpEntity<>(createBody, authHeaders(devToken)), Map.class);

        // DEVELOPER forbidden from listing everyone's pipelines
        ResponseEntity<Map> forbidden = restTemplate.exchange(
                "/api/pipelines", HttpMethod.GET, new HttpEntity<>(authHeaders(devToken)), Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // ADMIN sees at least: the seeded "Global Validation Pipeline" (V3) + the one just created
        ResponseEntity<List> allPipelines = restTemplate.exchange(
                "/api/pipelines", HttpMethod.GET, new HttpEntity<>(authHeaders(admin)), List.class);
        assertThat(allPipelines.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allPipelines.getBody().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void createPipeline_referencingUnknownProvider_returnsNotFound() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");

        Map<String, Object> createBody = Map.of(
                "name", "Broken Pipeline",
                "version", "1.0",
                "inputFormat", "JSON",
                "outputFormat", "JSON",
                "providers", List.of(Map.of("providerId", 999999, "httpMethod", "POST")));

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines", HttpMethod.POST, new HttpEntity<>(createBody, authHeaders(devToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
