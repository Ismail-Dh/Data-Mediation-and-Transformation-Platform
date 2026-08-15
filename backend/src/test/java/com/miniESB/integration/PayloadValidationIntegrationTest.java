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
 * Moteur de validation structurelle (niveau 1) : un DEVELOPER définit le schéma attendu
 * d'un pipeline (PipelineField), puis les payloads soumis sont validés contre ce schéma
 * en base réelle — champs requis manquants, types incorrects, etc.
 */
class PayloadValidationIntegrationTest extends AbstractIntegrationTest {

    private Long createPipeline(String devToken) {
        Map<String, Object> createBody = Map.of(
                "name", "Validation Pipeline " + uniqueUsername("vp"),
                "version", "1.0",
                "inputFormat", "JSON",
                "outputFormat", "JSON",
                "providers", List.of());
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines", HttpMethod.POST, new HttpEntity<>(createBody, authHeaders(devToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private void addField(String devToken, Long pipelineId, String path, String type, boolean required, boolean nullable) {
        Map<String, Object> fieldBody = Map.of(
                "fieldPath", path, "fieldType", type, "required", required, "nullable", nullable);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/fields", HttpMethod.POST,
                new HttpEntity<>(fieldBody, authHeaders(devToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private Long createPipelineWithSchema(String devToken) {
        Long pipelineId = createPipeline(devToken);
        addField(devToken, pipelineId, "orderId", "STRING", true, false);
        addField(devToken, pipelineId, "amount", "INTEGER", true, false);
        return pipelineId;
    }

    @Test
    void validPayload_isAcceptedAndPersistedAsValidated() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipelineWithSchema(devToken);

        Map<String, String> payloadBody = Map.of(
                "rawContent", "{\"orderId\":\"ORD-1\",\"amount\":42}",
                "format", "JSON");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/payloads", HttpMethod.POST,
                new HttpEntity<>(payloadBody, authHeaders(devToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status")).isEqualTo("VALIDATED");
        Long payloadId = ((Number) response.getBody().get("id")).longValue();

        // re-fetch from DB confirms the status was actually persisted, not just returned
        ResponseEntity<Map> getResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/payloads/" + payloadId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(devToken)), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("status")).isEqualTo("VALIDATED");

        // it also shows up when listing the pipeline's payloads
        ResponseEntity<List> listResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/payloads", HttpMethod.GET,
                new HttpEntity<>(authHeaders(devToken)), List.class);
        assertThat(listResponse.getBody()).hasSize(1);
    }

    @Test
    void payloadMissingRequiredField_isRejectedWith422_andNothingIsPersisted() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipelineWithSchema(devToken);

        // "amount" is missing
        Map<String, String> payloadBody = Map.of(
                "rawContent", "{\"orderId\":\"ORD-2\"}",
                "format", "JSON");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/payloads", HttpMethod.POST,
                new HttpEntity<>(payloadBody, authHeaders(devToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("violationCount")).isEqualTo(1);
        List<?> violations = (List<?>) response.getBody().get("violations");
        Map<?, ?> violation = (Map<?, ?>) violations.get(0);
        assertThat(violation.get("fieldPath")).isEqualTo("amount");
        assertThat(violation.get("errorType")).isEqualTo("MISSING_FIELD");

        // NB: receivePayload() is @Transactional, and PayloadValidationException is a
        // RuntimeException, so the whole transaction (including the initial payload
        // INSERT) is rolled back on structural-validation failure. Nothing is persisted —
        // there is no audit trail of rejected payloads. Worth reconsidering if traceability
        // of rejected submissions matters (e.g. save-then-validate in separate transactions).
        ResponseEntity<List> listResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/payloads", HttpMethod.GET,
                new HttpEntity<>(authHeaders(devToken)), List.class);
        assertThat(listResponse.getBody()).isEmpty();
    }

    @Test
    void payloadWithWrongFieldType_isRejectedWith422() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipelineWithSchema(devToken);

        // "amount" sent as a string instead of an integer
        Map<String, String> payloadBody = Map.of(
                "rawContent", "{\"orderId\":\"ORD-3\",\"amount\":\"forty-two\"}",
                "format", "JSON");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/payloads", HttpMethod.POST,
                new HttpEntity<>(payloadBody, authHeaders(devToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        List<?> violations = (List<?>) response.getBody().get("violations");
        assertThat(violations).anySatisfy(v -> {
            Map<?, ?> violation = (Map<?, ?>) v;
            assertThat(violation.get("fieldPath")).isEqualTo("amount");
            assertThat(violation.get("errorType")).isEqualTo("TYPE_MISMATCH");
        });
    }

    @Test
    void payloadOnPipelineWithoutSchema_skipsValidationAndIsAccepted() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipeline(devToken); // no PipelineField defined

        Map<String, String> payloadBody = Map.of("rawContent", "{\"anything\":true}", "format", "JSON");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/payloads", HttpMethod.POST,
                new HttpEntity<>(payloadBody, authHeaders(devToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status")).isEqualTo("VALIDATED");
    }


}
