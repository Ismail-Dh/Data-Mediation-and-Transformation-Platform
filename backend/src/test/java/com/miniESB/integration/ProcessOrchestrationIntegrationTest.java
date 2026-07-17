package com.miniESB.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flux complet {@code POST /api/process} : le seul endpoint qui enchaîne réellement
 * validation structurelle → mapping entrant → dispatch HTTP vers le(s) provider(s) →
 * validation/mapping de la réponse → agrégation.
 *
 * <p>Le provider externe est simulé avec un {@link HttpServer} du JDK plutôt qu'avec
 * WireMock — aucune dépendance supplémentaire, donc aucun risque de conflit de version
 * (ex. Jetty) avec le reste du classpath du projet.</p>
 */
class ProcessOrchestrationIntegrationTest extends AbstractIntegrationTest {

    private HttpServer stubServer;
    private final List<String> receivedBodies = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startStubServer() throws IOException {
        receivedBodies.clear();
        stubServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stubServer.setExecutor(Executors.newSingleThreadExecutor());
        stubServer.start();
    }

    @AfterEach
    void stopStubServer() {
        if (stubServer != null) {
            stubServer.stop(0);
        }
    }

    /** Registers a stub at {@code path} that always returns the given status/body, and records incoming bodies. */
    private void stub(String path, int status, String responseBody) {
        stubServer.createContext(path, exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedBodies.add(body);
            byte[] resp = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, resp.length);
            try (var os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
    }

    private int stubPort() {
        return stubServer.getAddress().getPort();
    }

    private Long createStubProvider(String adminToken, String path) {
        Map<String, Object> body = Map.of(
                "name", "Stub Provider " + uniqueUsername("sp"),
                "endpoint", "http://localhost:" + stubPort() + path,
                "protocol", "HTTP",
                "timeout", 5);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/providers", HttpMethod.POST, new HttpEntity<>(body, authHeaders(adminToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private Long createPipeline(String devToken, Long providerId) {
        Map<String, Object> body = Map.of(
                "name", "Process Pipeline " + uniqueUsername("pp"),
                "version", "1.0",
                "inputFormat", "JSON",
                "outputFormat", "JSON",
                "providers", List.of(Map.of("providerId", providerId, "httpMethod", "POST")));
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines", HttpMethod.POST, new HttpEntity<>(body, authHeaders(devToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private Long createPipelineWithoutProvider(String devToken) {
        Map<String, Object> body = Map.of(
                "name", "No Provider Pipeline " + uniqueUsername("np"),
                "version", "1.0",
                "inputFormat", "JSON",
                "outputFormat", "JSON",
                "providers", List.of());
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines", HttpMethod.POST, new HttpEntity<>(body, authHeaders(devToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private void addMappingRule(String devToken, Long pipelineId, String sourceField, String targetField) {
        Map<String, Object> body = new HashMap<>();
        body.put("sourceField", sourceField);
        body.put("targetField", targetField);
        body.put("mappingType", "FIELD_PLACEMENT");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/mappings", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(devToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private void addResponseMappingRule(String devToken, Long pipelineId, String sourceField, String targetField,
                                         boolean required) {
        Map<String, Object> body = new HashMap<>();
        body.put("sourceField", sourceField);
        body.put("targetField", targetField);
        body.put("mappingType", "FIELD_PLACEMENT");
        body.put("required", required);
        body.put("providerId", null);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/response-rules", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(devToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<Map> process(String devToken, Long pipelineId, String rawContent) {
        Map<String, Object> body = Map.of(
                "pipelineId", pipelineId, "rawContent", rawContent, "inputFormat", "JSON");
        return restTemplate.exchange(
                "/api/process", HttpMethod.POST, new HttpEntity<>(body, authHeaders(devToken)), Map.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void fullFlow_mapsInboundPayload_dispatchesToProvider_andMapsAggregatesResponse() {
        String admin = adminToken();
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long providerId = createStubProvider(admin, "/receive");
        Long pipelineId = createPipeline(devToken, providerId);

        addMappingRule(devToken, pipelineId, "customerName", "client");
        addResponseMappingRule(devToken, pipelineId, "status", "orderStatus", true);

        stub("/receive", 200, "{\"status\":\"OK\",\"confirmationId\":\"XYZ-1\"}");

        ResponseEntity<Map> response = process(devToken, pipelineId, "{\"customerName\":\"Alice\",\"amount\":10}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("overallSuccess")).isEqualTo(true);
        assertThat(((Number) body.get("providerCount")).intValue()).isEqualTo(1);
        assertThat(((Number) body.get("successCount")).intValue()).isEqualTo(1);

        Map<String, Object> aggregated = (Map<String, Object>) body.get("aggregatedBody");
        assertThat(aggregated.get("orderStatus")).isEqualTo("OK");

        List<Map<String, Object>> details = (List<Map<String, Object>>) body.get("providerDetails");
        assertThat(details).hasSize(1);
        assertThat(details.get(0).get("dispatchSuccess")).isEqualTo(true);
        assertThat(details.get(0).get("validationPassed")).isEqualTo(true);
        assertThat(((Number) details.get(0).get("httpStatus")).intValue()).isEqualTo(200);

        // the MAPPED payload was sent to the provider, not the raw one
        assertThat(receivedBodies).hasSize(1);
        String sentBody = receivedBodies.get(0);
        assertThat(sentBody).contains("\"client\"").contains("Alice").doesNotContain("customerName");
    }

    @Test
    void providerReturning500_isReportedAsDispatchFailure_andOverallProcessFails() {
        String admin = adminToken();
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long providerId = createStubProvider(admin, "/fail");
        Long pipelineId = createPipeline(devToken, providerId);

        stub("/fail", 500, "boom");

        ResponseEntity<Map> response = process(devToken, pipelineId, "{\"anything\":true}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("overallSuccess")).isEqualTo(false);
        assertThat(((Number) body.get("successCount")).intValue()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) body.get("providerDetails");
        assertThat(details.get(0).get("dispatchSuccess")).isEqualTo(false);
        assertThat(((Number) details.get(0).get("httpStatus")).intValue()).isEqualTo(500);
    }

    @Test
    void providerResponseMissingRequiredField_failsResponseValidation_butDispatchStillCountsAsSuccess() {
        String admin = adminToken();
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long providerId = createStubProvider(admin, "/incomplete");
        Long pipelineId = createPipeline(devToken, providerId);

        addResponseMappingRule(devToken, pipelineId, "confirmationId", "confirmationCode", true);

        stub("/incomplete", 200, "{\"status\":\"OK\"}"); // confirmationId missing

        ResponseEntity<Map> response = process(devToken, pipelineId, "{\"anything\":true}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        // the HTTP call itself succeeded...
        assertThat(body.get("overallSuccess")).isEqualTo(true);
        // ...but the response failed its own validation, so it doesn't count as a "success"
        // and its fields are excluded from the aggregated body
        assertThat(((Number) body.get("successCount")).intValue()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) body.get("providerDetails");
        assertThat(details.get(0).get("dispatchSuccess")).isEqualTo(true);
        assertThat(details.get(0).get("validationPassed")).isEqualTo(false);
        assertThat((List<?>) details.get(0).get("validationErrors")).isNotEmpty();

        @SuppressWarnings("unchecked")
        Map<String, Object> aggregated = (Map<String, Object>) body.get("aggregatedBody");
        assertThat(aggregated).isEmpty();
    }

    @Test
    void payloadFailingStructuralValidation_shortCircuitsBeforeAnyDispatch() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipelineWithoutProvider(devToken);

        // add a schema requiring "orderId"
        Map<String, Object> fieldBody = Map.of(
                "fieldPath", "orderId", "fieldType", "STRING", "required", true, "nullable", false);
        ResponseEntity<Map> fieldResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/fields", HttpMethod.POST,
                new HttpEntity<>(fieldBody, authHeaders(devToken)), Map.class);
        assertThat(fieldResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> response = process(devToken, pipelineId, "{\"somethingElse\":true}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(receivedBodies).isEmpty();
    }

    @Test
    void unknownPipeline_returnsNotFound() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");

        ResponseEntity<Map> response = process(devToken, 999999L, "{\"a\":1}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
