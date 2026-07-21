package com.miniESB.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.enums.DataFormat;
import com.miniESB.engine.dispatch.DispatchOutcome;
import com.miniESB.engine.dispatch.HttpDispatchExecutor;
import com.miniESB.engine.mapping.MappingEngine;
import com.miniESB.engine.mapping.MappingRuleData;
import com.miniESB.engine.validation.ValidationEngine;
import com.miniESB.exception.FieldViolation;
import com.miniESB.service.EngineProcessService;
import com.miniESB.service.EngineResponseMappingHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link EngineProcessService} (mode "engine" — fichier
 * {@code rules.json}, sans base de données).
 *
 * <p>{@code rulesFilePath} est un champ {@code @Value} (non final), donc
 * {@code @InjectMocks} ne le remplit pas — on le fixe par réflexion dans
 * {@code setUp()} pour éviter un {@code NullPointerException} sur
 * {@code new File(rulesFilePath)}.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EngineProcessService")
class EngineProcessServiceTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private MappingEngine mappingEngine;
    @Mock private ValidationEngine validationEngine;
    @Mock private HttpDispatchExecutor dispatchExecutor;
    @Mock private EngineResponseMappingHelper responseMappingHelper;

    @InjectMocks
    private EngineProcessService service;

    private static final String RAW_CONTENT = "{\"firstName\":\"Aya\"}";

    @BeforeEach
    void setUp() throws Exception {
        Field field = EngineProcessService.class.getDeclaredField("rulesFilePath");
        field.setAccessible(true);
        field.set(service, "/app/rules.json");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void stubLoadRules(Map<String, Object> rules) throws Exception {
        when(objectMapper.readValue(any(java.io.File.class), any(TypeReference.class)))
                .thenReturn(rules);
    }

    @SuppressWarnings("unchecked")
    private void stubParseInput(Map<String, Object> input) throws Exception {
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(new LinkedHashMap<>(input));
    }

    private Map<String, Object> baseRules() {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("mappingRules", List.of());
        rules.put("validationFields", List.of());
        rules.put("validationRules", List.of());
        rules.put("providers", List.of());
        rules.put("outputFormat", "JSON");
        rules.put("responseMappingRules", List.of());
        return rules;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Happy path
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("returns mappedPayload, providerResults and overallSuccess when everything succeeds")
        void process_allStepsSucceed_returnsCompleteResult() throws Exception {
            Map<String, Object> rules = baseRules();
            Map<String, Object> provider = Map.of(
                    "id", 5, "name", "Billing", "endpoint", "https://billing.example.com",
                    "timeout", 10, "httpMethod", "POST");
            rules.put("providers", List.of(provider));

            stubLoadRules(rules);
            stubParseInput(Map.of("firstName", "Aya"));
            when(validationEngine.validate(anyList(), any())).thenReturn(List.of());
            Map<String, Object> mapped = Map.of("first_name", "Aya");
            when(mappingEngine.apply(anyList(), anyMap())).thenReturn(mapped);
            when(objectMapper.writeValueAsString(mapped)).thenReturn("{\"first_name\":\"Aya\"}");
            when(dispatchExecutor.call(eq("https://billing.example.com"), eq(HttpMethod.POST),
                    anyString(), eq(DataFormat.JSON), eq(10), eq("Billing")))
                    .thenReturn(DispatchOutcome.success(200, "{\"ok\":true}", 42L));

            Map<String, Object> result = service.process(RAW_CONTENT);

            assertThat(result.get("mappedPayload")).isEqualTo(mapped);
            assertThat(result.get("overallSuccess")).isEqualTo(true);
            List<Map<String, Object>> providerResults = (List<Map<String, Object>>) result.get("providerResults");
            assertThat(providerResults).hasSize(1);
            assertThat(providerResults.get(0)).containsEntry("providerName", "Billing")
                    .containsEntry("success", true)
                    .containsEntry("rawBody", "{\"ok\":true}");
        }

        @Test
        @DisplayName("applies response mapping only when the provider call succeeded")
        void process_successfulDispatch_appliesResponseMapping() throws Exception {
            Map<String, Object> rules = baseRules();
            Map<String, Object> provider = Map.of("id", 5, "name", "Billing", "endpoint", "https://x");
            rules.put("providers", List.of(provider));

            stubLoadRules(rules);
            stubParseInput(Map.of());
            when(validationEngine.validate(anyList(), any())).thenReturn(List.of());
            when(mappingEngine.apply(anyList(), anyMap())).thenReturn(Map.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(dispatchExecutor.call(anyString(), any(), anyString(), any(), anyInt(), anyString()))
                    .thenReturn(DispatchOutcome.success(200, "{\"a\":1}", 10L));

            service.process(RAW_CONTENT);

            verify(responseMappingHelper).applyResponseMapping(eq(5L), eq("{\"a\":1}"), anyList(), anyMap());
        }

        @Test
        @DisplayName("never applies response mapping when the provider call failed")
        void process_failedDispatch_neverAppliesResponseMapping() throws Exception {
            Map<String, Object> rules = baseRules();
            rules.put("providers", List.of(Map.of("id", 5, "name", "Billing", "endpoint", "https://x")));

            stubLoadRules(rules);
            stubParseInput(Map.of());
            when(validationEngine.validate(anyList(), any())).thenReturn(List.of());
            when(mappingEngine.apply(anyList(), anyMap())).thenReturn(Map.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(dispatchExecutor.call(anyString(), any(), anyString(), any(), anyInt(), anyString()))
                    .thenReturn(DispatchOutcome.httpError(500, "boom", 5L));

            Map<String, Object> result = service.process(RAW_CONTENT);

            verifyNoInteractions(responseMappingHelper);
            List<Map<String, Object>> providerResults = (List<Map<String, Object>>) result.get("providerResults");
            assertThat(providerResults.get(0)).containsEntry("success", false);
            assertThat(result.get("overallSuccess")).isEqualTo(false);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Validation — niveau 1 (structurel)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Structural validation (level 1)")
    class StructuralValidation {

        @Test
        @DisplayName("throws IllegalArgumentException when a required field is missing")
        void process_missingRequiredField_throwsIllegalArgument() throws Exception {
            Map<String, Object> rules = baseRules();
            rules.put("validationFields", List.of(Map.of("fieldPath", "email", "required", true, "nullable", true)));

            stubLoadRules(rules);
            stubParseInput(Map.of("firstName", "Aya")); // pas de "email"

            assertThatThrownBy(() -> service.process(RAW_CONTENT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("email");

            verifyNoInteractions(mappingEngine, dispatchExecutor);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when a non-nullable field is null")
        void process_nonNullableFieldIsNull_throwsIllegalArgument() throws Exception {
            Map<String, Object> rules = baseRules();
            rules.put("validationFields", List.of(Map.of("fieldPath", "age", "required", false, "nullable", false)));

            stubLoadRules(rules);
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("age", null);
            stubParseInput(input);

            assertThatThrownBy(() -> service.process(RAW_CONTENT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("age");
        }

        @Test
        @DisplayName("does not fail when a non-required field is simply absent")
        void process_optionalFieldAbsent_doesNotThrow() throws Exception {
            Map<String, Object> rules = baseRules();
            rules.put("validationFields", List.of(Map.of("fieldPath", "nickname", "required", false, "nullable", true)));

            stubLoadRules(rules);
            stubParseInput(Map.of("firstName", "Aya"));
            when(validationEngine.validate(anyList(), any())).thenReturn(List.of());
            when(mappingEngine.apply(anyList(), anyMap())).thenReturn(Map.of());

            assertThatCode(() -> service.process(RAW_CONTENT)).doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Validation — niveau 2 (métier, ValidationEngine)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Business validation (level 2)")
    class BusinessValidation {

        @Test
        @DisplayName("throws IllegalArgumentException when ValidationEngine reports violations")
        void process_businessViolations_throwsIllegalArgument() throws Exception {
            Map<String, Object> rules = baseRules();
            stubLoadRules(rules);
            stubParseInput(Map.of("email", "not-an-email"));
            when(validationEngine.validate(anyList(), any())).thenReturn(
                    List.of(new FieldViolation("email", "INVALID_FORMAT", "bad email")));

            assertThatThrownBy(() -> service.process(RAW_CONTENT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Business validation failed");

            verifyNoInteractions(mappingEngine, dispatchExecutor);
        }

        @Test
        @DisplayName("skips unknown RuleType entries instead of failing")
        void process_unknownRuleType_isFilteredOutSilently() throws Exception {
            Map<String, Object> rules = baseRules();
            rules.put("validationRules", List.of(
                    Map.of("fieldName", "x", "ruleType", "NOT_A_REAL_TYPE", "pattern", "")));
            stubLoadRules(rules);
            stubParseInput(Map.of());
            when(validationEngine.validate(anyList(), any())).thenReturn(List.of());
            when(mappingEngine.apply(anyList(), anyMap())).thenReturn(Map.of());

            assertThatCode(() -> service.process(RAW_CONTENT)).doesNotThrowAnyException();

            verify(validationEngine).validate(argThat(List::isEmpty), any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Mapping
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Mapping rules extraction")
    class MappingExtraction {

        @Test
        @DisplayName("skips mapping rules with an unsupported mappingType")
        void process_unsupportedMappingType_isFilteredOut() throws Exception {
            Map<String, Object> rules = baseRules();
            rules.put("mappingRules", List.of(
                    Map.of("sourceField", "a", "targetField", "b", "mappingType", "NOT_A_TYPE")));
            stubLoadRules(rules);
            stubParseInput(Map.of());
            when(validationEngine.validate(anyList(), any())).thenReturn(List.of());
            when(mappingEngine.apply(anyList(), anyMap())).thenReturn(Map.of());

            service.process(RAW_CONTENT);

            ArgumentCaptor<List<MappingRuleData>> captor = ArgumentCaptor.forClass(List.class);
            verify(mappingEngine).apply(captor.capture(), anyMap());
            assertThat(captor.getValue()).isEmpty();
        }

        @Test
        @DisplayName("trims the expression of a valid mapping rule")
        void process_mappingRuleWithExpression_trimsExpression() throws Exception {
            Map<String, Object> rules = baseRules();
            rules.put("mappingRules", List.of(
                    Map.of("sourceField", "a", "targetField", "b",
                            "mappingType", "FIELD_PLACEMENT", "expression", "  upper(a)  ")));
            stubLoadRules(rules);
            stubParseInput(Map.of());
            when(validationEngine.validate(anyList(), any())).thenReturn(List.of());
            when(mappingEngine.apply(anyList(), anyMap())).thenReturn(Map.of());

            service.process(RAW_CONTENT);

            ArgumentCaptor<List<MappingRuleData>> captor = ArgumentCaptor.forClass(List.class);
            verify(mappingEngine).apply(captor.capture(), anyMap());
            assertThat(captor.getValue()).hasSize(1);
            assertThat(captor.getValue().get(0).expression()).isEqualTo("upper(a)");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Dispatch providers
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Provider dispatch")
    class ProviderDispatch {

        @Test
        @DisplayName("skips dispatch entirely and reports overallSuccess=false when no providers configured")
        void process_noProviders_skipsDispatch() throws Exception {
            Map<String, Object> rules = baseRules(); // providers = List.of()
            stubLoadRules(rules);
            stubParseInput(Map.of());
            when(validationEngine.validate(anyList(), any())).thenReturn(List.of());
            when(mappingEngine.apply(anyList(), anyMap())).thenReturn(Map.of());

            Map<String, Object> result = service.process(RAW_CONTENT);

            assertThat((List<?>) result.get("providerResults")).isEmpty();
            assertThat(result.get("overallSuccess")).isEqualTo(false);
            verifyNoInteractions(dispatchExecutor);
        }

        @Test
        @DisplayName("uses HttpMethod.POST when httpMethod is a genuinely valid standard method")
        void process_standardHttpMethod_isUsedAsIs() throws Exception {
            Map<String, Object> rules = baseRules();
            rules.put("providers", List.of(Map.of(
                    "id", 1, "name", "X", "endpoint", "https://x", "httpMethod", "PUT")));
            stubLoadRules(rules);
            stubParseInput(Map.of());
            when(validationEngine.validate(anyList(), any())).thenReturn(List.of());
            when(mappingEngine.apply(anyList(), anyMap())).thenReturn(Map.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(dispatchExecutor.call(anyString(), any(), anyString(), any(), anyInt(), anyString()))
                    .thenReturn(DispatchOutcome.success(200, "ok", 1L));

            service.process(RAW_CONTENT);

            verify(dispatchExecutor).call(anyString(), eq(HttpMethod.PUT), anyString(), any(), anyInt(), anyString());
        }

        /**
         * ⚠️ Depuis Spring Framework 6, {@code HttpMethod} n'est plus un enum strict :
         * {@code HttpMethod.valueOf(String)} accepte n'importe quelle chaîne et construit
         * une méthode HTTP personnalisée au lieu de lever une {@code IllegalArgumentException}.
         * Le bloc {@code catch (IllegalArgumentException)} dans {@code callProvider()},
         * censé faire un fallback vers POST, est donc actuellement DU CODE MORT : une
         * valeur farfelue dans rules.json ("FOOBAR") passe telle quelle au dispatch au
         * lieu d'être normalisée. Ce test documente le comportement RÉEL, pas celui voulu
         * par le commentaire du code — à corriger côté service si le fallback est important
         * (ex: valider httpMethodStr contre un Set de méthodes connues avant l'appel).
         */
        @Test
        @DisplayName("[BUG] does NOT actually fall back to POST for a non-standard httpMethod — passes it through as a custom method")
        void process_nonStandardHttpMethod_isPassedThroughInsteadOfFallingBackToPost() throws Exception {
            Map<String, Object> rules = baseRules();
            rules.put("providers", List.of(Map.of(
                    "id", 1, "name", "X", "endpoint", "https://x", "httpMethod", "FOOBAR")));
            stubLoadRules(rules);
            stubParseInput(Map.of());
            when(validationEngine.validate(anyList(), any())).thenReturn(List.of());
            when(mappingEngine.apply(anyList(), anyMap())).thenReturn(Map.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(dispatchExecutor.call(anyString(), any(), anyString(), any(), anyInt(), anyString()))
                    .thenReturn(DispatchOutcome.success(200, "ok", 1L));

            service.process(RAW_CONTENT);

            verify(dispatchExecutor).call(
                    anyString(), eq(HttpMethod.valueOf("FOOBAR")), anyString(), any(), anyInt(), anyString());
        }

        @Test
        @DisplayName("defaults timeout to 30 seconds when not specified for a provider")
        void process_missingTimeout_defaultsTo30Seconds() throws Exception {
            Map<String, Object> rules = baseRules();
            rules.put("providers", List.of(Map.of("id", 1, "name", "X", "endpoint", "https://x")));
            stubLoadRules(rules);
            stubParseInput(Map.of());
            when(validationEngine.validate(anyList(), any())).thenReturn(List.of());
            when(mappingEngine.apply(anyList(), anyMap())).thenReturn(Map.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(dispatchExecutor.call(anyString(), any(), anyString(), any(), anyInt(), anyString()))
                    .thenReturn(DispatchOutcome.success(200, "ok", 1L));

            service.process(RAW_CONTENT);

            verify(dispatchExecutor).call(anyString(), any(), anyString(), any(), eq(30), anyString());
        }

        @Test
        @DisplayName("falls back to JSON when outputFormat in rules.json is invalid")
        void process_invalidOutputFormat_fallsBackToJson() throws Exception {
            Map<String, Object> rules = baseRules();
            rules.put("outputFormat", "NOT_A_FORMAT");
            rules.put("providers", List.of(Map.of("id", 1, "name", "X", "endpoint", "https://x")));
            stubLoadRules(rules);
            stubParseInput(Map.of());
            when(validationEngine.validate(anyList(), any())).thenReturn(List.of());
            when(mappingEngine.apply(anyList(), anyMap())).thenReturn(Map.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(dispatchExecutor.call(anyString(), any(), anyString(), any(), anyInt(), anyString()))
                    .thenReturn(DispatchOutcome.success(200, "ok", 1L));

            service.process(RAW_CONTENT);

            verify(dispatchExecutor).call(anyString(), any(), anyString(), eq(DataFormat.JSON), anyInt(), anyString());
        }

        @Test
        @DisplayName("dispatches independently to every provider — one failure does not stop the others")
        void process_multipleProviders_dispatchesToAllIndependently() throws Exception {
            Map<String, Object> rules = baseRules();
            rules.put("providers", List.of(
                    Map.of("id", 1, "name", "A", "endpoint", "https://a"),
                    Map.of("id", 2, "name", "B", "endpoint", "https://b")));
            stubLoadRules(rules);
            stubParseInput(Map.of());
            when(validationEngine.validate(anyList(), any())).thenReturn(List.of());
            when(mappingEngine.apply(anyList(), anyMap())).thenReturn(Map.of());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            when(dispatchExecutor.call(eq("https://a"), any(), anyString(), any(), anyInt(), eq("A")))
                    .thenReturn(DispatchOutcome.httpError(500, "fail", 1L));
            when(dispatchExecutor.call(eq("https://b"), any(), anyString(), any(), anyInt(), eq("B")))
                    .thenReturn(DispatchOutcome.success(200, "ok", 1L));

            Map<String, Object> result = service.process(RAW_CONTENT);

            List<Map<String, Object>> providerResults = (List<Map<String, Object>>) result.get("providerResults");
            assertThat(providerResults).hasSize(2);
            assertThat(result.get("overallSuccess")).isEqualTo(true); // au moins un succès
        }

        @Test
        @DisplayName("throws IllegalStateException when the mapped payload cannot be serialized")
        void process_payloadSerializationFails_throwsIllegalState() throws Exception {
            Map<String, Object> rules = baseRules();
            rules.put("providers", List.of(Map.of("id", 1, "name", "X", "endpoint", "https://x")));
            stubLoadRules(rules);
            stubParseInput(Map.of());
            when(validationEngine.validate(anyList(), any())).thenReturn(List.of());
            when(mappingEngine.apply(anyList(), anyMap())).thenReturn(Map.of());
            when(objectMapper.writeValueAsString(any()))
                    .thenThrow(new JsonProcessingException("boom") {});

            assertThatThrownBy(() -> service.process(RAW_CONTENT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to serialize mapped payload");

            verifyNoInteractions(dispatchExecutor);
        }
    }
}