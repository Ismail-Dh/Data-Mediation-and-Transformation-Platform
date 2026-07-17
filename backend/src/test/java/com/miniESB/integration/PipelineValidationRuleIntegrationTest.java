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
 * Règles de validation métier (niveau 2) : CRUD via {@code PipelineValidationRuleController}
 * (règles privées + rattachement de règles globales admin), et vérification de leur effet
 * RÉEL via {@code POST /api/pipelines/{id}/validation/preview} — le seul endpoint, avec le
 * Sandbox, qui exécute effectivement {@code BusinessValidatorService}.
 *
 * <p>NB découvert en écrivant ces tests : {@code ProcessOrchestrationServiceImpl} (le flux
 * {@code /api/process}, voir {@link ProcessOrchestrationIntegrationTest}) n'invoque QUE la
 * validation structurelle (niveau 1) via {@code PayloadService.receivePayload} — il n'appelle
 * jamais {@code BusinessValidatorService}. Les règles niveau 2 attachées ici n'ont donc aucun
 * effet sur le flux de traitement réel ; elles ne sont exercées que par ce endpoint de preview
 * et par {@code /sandbox/run}. À signaler si le niveau 2 est censé bloquer le traitement réel.</p>
 */
class PipelineValidationRuleIntegrationTest extends AbstractIntegrationTest {

    private Long createPipeline(String devToken) {
        Map<String, Object> createBody = Map.of(
                "name", "Rules Pipeline " + uniqueUsername("rp"),
                "version", "1.0",
                "inputFormat", "JSON",
                "outputFormat", "JSON",
                "providers", List.of());
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/pipelines", HttpMethod.POST, new HttpEntity<>(createBody, authHeaders(devToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private ResponseEntity<Map> createPrivateRule(String devToken, Long pipelineId, String fieldName,
                                                   String ruleType, String pattern) {
        Map<String, Object> body = new HashMap<>();
        body.put("fieldName", fieldName);
        body.put("ruleType", ruleType);
        body.put("pattern", pattern);
        body.put("description", "test rule");
        body.put("active", true);
        return restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/validation-rules", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(devToken)), Map.class);
    }

    private ResponseEntity<Map> preview(String devToken, Long pipelineId, String rawContent) {
        Map<String, String> body = Map.of("rawContent", rawContent, "format", "JSON");
        return restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/validation/preview", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(devToken)), Map.class);
    }

    @Test
    void privateNotNullRule_isEnforced_andMovesPipelineToConfigured() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipeline(devToken);

        ResponseEntity<Map> createResponse = createPrivateRule(devToken, pipelineId, "email", "NOT_NULL", null);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody().get("global")).isEqualTo(false);

        // side effect: DRAFT -> CONFIGURED, same as mapping rules
        ResponseEntity<Map> pipelineAfter = restTemplate.exchange(
                "/api/pipelines/" + pipelineId, HttpMethod.GET, new HttpEntity<>(authHeaders(devToken)), Map.class);
        assertThat(pipelineAfter.getBody().get("status")).isEqualTo("CONFIGURED");

        // field missing -> business validation fails
        ResponseEntity<Map> failing = preview(devToken, pipelineId, "{\"amount\":10}");
        assertThat(failing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(failing.getBody().get("structuralOk")).isEqualTo(true); // no PipelineField schema defined
        assertThat(failing.getBody().get("businessOk")).isEqualTo(false);
        assertThat(failing.getBody().get("valid")).isEqualTo(false);
        List<?> violations = (List<?>) failing.getBody().get("violations");
        assertThat(violations).hasSize(1);
        assertThat(((Map<?, ?>) violations.get(0)).get("fieldPath")).isEqualTo("email");

        // field present -> passes
        ResponseEntity<Map> passing = preview(devToken, pipelineId, "{\"email\":\"alice@example.com\"}");
        assertThat(passing.getBody().get("businessOk")).isEqualTo(true);
        assertThat(passing.getBody().get("valid")).isEqualTo(true);
    }

    @Test
    void privateRegexEmailRule_rejectsMalformedValue() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipeline(devToken);
        createPrivateRule(devToken, pipelineId, "email", "REGEX_EMAIL", "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

        ResponseEntity<Map> invalid = preview(devToken, pipelineId, "{\"email\":\"not-an-email\"}");
        assertThat(invalid.getBody().get("businessOk")).isEqualTo(false);
        List<?> violations = (List<?>) invalid.getBody().get("violations");
        assertThat(((Map<?, ?>) violations.get(0)).get("errorType")).isEqualTo("INVALID_FORMAT");

        ResponseEntity<Map> valid = preview(devToken, pipelineId, "{\"email\":\"alice@example.com\"}");
        assertThat(valid.getBody().get("businessOk")).isEqualTo(true);
    }

    @Test
    void attachingGlobalRule_copiesDefinitionAndMarksItGlobal() {
        // GlobalValidationRuleController is documented "ADMIN only" but its actual
        // @PreAuthorize is hasAnyRole('DEVELOPER','ADMIN') — using the admin account
        // here regardless, since that's the intended usage.
        String admin = adminToken();
        Map<String, Object> globalRuleBody = Map.of(
                "fieldName", "username",
                "ruleType", "MIN_MAX_LENGTH",
                "pattern", "3,10",
                "active", true,
                "description", "username length");
        ResponseEntity<Map> globalRuleResponse = restTemplate.exchange(
                "/api/admin/rules", HttpMethod.POST, new HttpEntity<>(globalRuleBody, authHeaders(admin)), Map.class);
        assertThat(globalRuleResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long globalRuleId = ((Number) globalRuleResponse.getBody().get("id")).longValue();

        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipeline(devToken);

        Map<String, Object> attachBody = new HashMap<>();
        attachBody.put("globalRuleId", globalRuleId);
        attachBody.put("active", true);
        ResponseEntity<Map> attachResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/validation-rules", HttpMethod.POST,
                new HttpEntity<>(attachBody, authHeaders(devToken)), Map.class);

        assertThat(attachResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(attachResponse.getBody().get("global")).isEqualTo(true);
        assertThat(attachResponse.getBody().get("fieldName")).isEqualTo("username");
        assertThat(attachResponse.getBody().get("ruleType")).isEqualTo("MIN_MAX_LENGTH");

        // the attached rule is actually enforced
        ResponseEntity<Map> tooShort = preview(devToken, pipelineId, "{\"username\":\"ab\"}");
        assertThat(tooShort.getBody().get("businessOk")).isEqualTo(false);

        ResponseEntity<Map> okLength = preview(devToken, pipelineId, "{\"username\":\"alice\"}");
        assertThat(okLength.getBody().get("businessOk")).isEqualTo(true);
    }

    @Test
    void updatingGlobalRule_throughPipelineEndpoint_isRejected() {
        String admin = adminToken();
        // NOT_NULL requires no pattern; using a HashMap since the pattern value is null.
        Map<String, Object> body2 = new HashMap<>();
        body2.put("fieldName", "phone");
        body2.put("ruleType", "NOT_NULL");
        body2.put("pattern", null);
        body2.put("active", true);
        body2.put("description", "");
        ResponseEntity<Map> globalRuleResponse = restTemplate.exchange(
                "/api/admin/rules", HttpMethod.POST, new HttpEntity<>(body2, authHeaders(admin)), Map.class);
        assertThat(globalRuleResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long globalRuleId = ((Number) globalRuleResponse.getBody().get("id")).longValue();

        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipeline(devToken);

        Map<String, Object> attachBody = new HashMap<>();
        attachBody.put("globalRuleId", globalRuleId);
        attachBody.put("active", true);
        ResponseEntity<Map> attachResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/validation-rules", HttpMethod.POST,
                new HttpEntity<>(attachBody, authHeaders(devToken)), Map.class);
        Long ruleId = ((Number) attachResponse.getBody().get("id")).longValue();

        ResponseEntity<Map> updateResponse = createPrivateRuleUpdate(devToken, pipelineId, ruleId);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Map> createPrivateRuleUpdate(String devToken, Long pipelineId, Long ruleId) {
        Map<String, Object> body = new HashMap<>();
        body.put("fieldName", "phone2");
        body.put("ruleType", "NOT_NULL");
        body.put("pattern", null);
        body.put("active", true);
        body.put("description", "");
        return restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/validation-rules/" + ruleId, HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(devToken)), Map.class);
    }

    @Test
    void toggleActive_disablesRuleEffectWithoutDeletingIt() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipeline(devToken);
        ResponseEntity<Map> createResponse = createPrivateRule(devToken, pipelineId, "email", "NOT_NULL", null);
        Long ruleId = ((Number) createResponse.getBody().get("id")).longValue();

        // initially enforced
        assertThat(preview(devToken, pipelineId, "{}").getBody().get("businessOk")).isEqualTo(false);

        ResponseEntity<Map> toggleResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/validation-rules/" + ruleId + "/toggle", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(devToken)), Map.class);
        assertThat(toggleResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(toggleResponse.getBody().get("active")).isEqualTo(false);

        // rule still listed, just inactive, and no longer enforced
        ResponseEntity<List> rulesList = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/validation-rules", HttpMethod.GET,
                new HttpEntity<>(authHeaders(devToken)), List.class);
        assertThat(rulesList.getBody()).hasSize(1);

        ResponseEntity<Map> nowPassing = preview(devToken, pipelineId, "{}");
        assertThat(nowPassing.getBody().get("businessOk")).isEqualTo(true);
    }

    @Test
    void deleteRule_removesItFromPipeline() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipeline(devToken);
        ResponseEntity<Map> createResponse = createPrivateRule(devToken, pipelineId, "email", "NOT_NULL", null);
        Long ruleId = ((Number) createResponse.getBody().get("id")).longValue();

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/validation-rules/" + ruleId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(devToken)), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List> rulesList = restTemplate.exchange(
                "/api/pipelines/" + pipelineId + "/validation-rules", HttpMethod.GET,
                new HttpEntity<>(authHeaders(devToken)), List.class);
        assertThat(rulesList.getBody()).isEmpty();
    }

    @Test
    void addingDuplicateRule_sameFieldAndType_isRejected() {
        String devToken = registerAndGetToken(uniqueUsername("dev"), "Password123!", "DEVELOPER");
        Long pipelineId = createPipeline(devToken);
        ResponseEntity<Map> first = createPrivateRule(devToken, pipelineId, "email", "NOT_NULL", null);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> duplicate = createPrivateRule(devToken, pipelineId, "email", "NOT_NULL", null);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
