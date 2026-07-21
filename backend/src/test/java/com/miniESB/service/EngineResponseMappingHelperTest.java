package com.miniESB.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.responsemapping.ResponseFieldRuleData;
import com.miniESB.engine.responsemapping.ResponseMappingEngine;
import com.miniESB.engine.responsemapping.ResponseMappingResult;
import com.miniESB.service.EngineResponseMappingHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link EngineResponseMappingHelper}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EngineResponseMappingHelper")
class EngineResponseMappingHelperTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private ResponseMappingEngine responseMappingEngine;

    @InjectMocks
    private EngineResponseMappingHelper helper;

    // ══════════════════════════════════════════════════════════════════════════
    //  Parsing du body brut du provider
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Invalid provider response body")
    class InvalidBody {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("marks validationPassed=false and never calls the engine when body is not valid JSON")
        void applyResponseMapping_invalidJson_marksFailedWithoutCallingEngine() throws Exception {
            when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                    .thenThrow(new JsonProcessingException("Unexpected character") {});

            Map<String, Object> result = new LinkedHashMap<>();
            helper.applyResponseMapping(1L, "not-json", List.of(), result);

            assertThat(result.get("validationPassed")).isEqualTo(false);
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) result.get("validationErrors");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0)).contains("not valid JSON");

            verifyNoInteractions(responseMappingEngine);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("does not set mappedBody when parsing fails (early return)")
        void applyResponseMapping_invalidJson_doesNotSetMappedBody() throws Exception {
            when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                    .thenThrow(new JsonProcessingException("boom") {});

            Map<String, Object> result = new LinkedHashMap<>();
            helper.applyResponseMapping(1L, "garbage", List.of(), result);

            assertThat(result).doesNotContainKey("mappedBody");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Filtrage des règles par provider
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rule filtering by providerId")
    class RuleFiltering {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("includes rules with providerId=null (generic rules apply to every provider)")
        void applyResponseMapping_genericRule_isAlwaysApplicable() throws Exception {
            when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                    .thenReturn(Map.of("status", "OK"));
            when(responseMappingEngine.apply(anyList(), anyMap()))
                    .thenReturn(new ResponseMappingResult(true, List.of(), Map.of()));

            // Note: Map.of ne permet pas de valeur null — on utilise une Map mutable
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("providerId", null);
            rule.put("sourceField", "status");
            rule.put("targetField", "orderStatus");
            rule.put("mappingType", "FIELD_PLACEMENT");
            rule.put("required", true);

            helper.applyResponseMapping(99L, "{}", List.of(rule), new LinkedHashMap<>());

            ArgumentCaptor<List<ResponseFieldRuleData>> captor = ArgumentCaptor.forClass(List.class);
            verify(responseMappingEngine).apply(captor.capture(), anyMap());
            assertThat(captor.getValue()).hasSize(1);
            assertThat(captor.getValue().get(0).sourceField()).isEqualTo("status");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("includes a rule scoped to the same providerId as the current call")
        void applyResponseMapping_ruleScopedToMatchingProvider_isIncluded() throws Exception {
            when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                    .thenReturn(Map.of());
            when(responseMappingEngine.apply(anyList(), anyMap()))
                    .thenReturn(new ResponseMappingResult(true, List.of(), Map.of()));

            Map<String, Object> rule = Map.of(
                    "providerId", 5, "sourceField", "a", "targetField", "b",
                    "mappingType", "FIELD_PLACEMENT", "required", false);

            helper.applyResponseMapping(5L, "{}", List.of(rule), new LinkedHashMap<>());

            ArgumentCaptor<List<ResponseFieldRuleData>> captor = ArgumentCaptor.forClass(List.class);
            verify(responseMappingEngine).apply(captor.capture(), anyMap());
            assertThat(captor.getValue()).hasSize(1);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("excludes a rule scoped to a different providerId than the current call")
        void applyResponseMapping_ruleScopedToOtherProvider_isExcluded() throws Exception {
            when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                    .thenReturn(Map.of());
            when(responseMappingEngine.apply(anyList(), anyMap()))
                    .thenReturn(new ResponseMappingResult(true, List.of(), Map.of()));

            Map<String, Object> rule = Map.of(
                    "providerId", 5, "sourceField", "a", "targetField", "b",
                    "mappingType", "FIELD_PLACEMENT", "required", false);

            helper.applyResponseMapping(6L, "{}", List.of(rule), new LinkedHashMap<>()); // providerId différent

            ArgumentCaptor<List<ResponseFieldRuleData>> captor = ArgumentCaptor.forClass(List.class);
            verify(responseMappingEngine).apply(captor.capture(), anyMap());
            assertThat(captor.getValue()).isEmpty();
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("excludes a provider-scoped rule when the current call has no providerId (null)")
        void applyResponseMapping_scopedRuleWithNullCurrentProviderId_isExcluded() throws Exception {
            when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                    .thenReturn(Map.of());
            when(responseMappingEngine.apply(anyList(), anyMap()))
                    .thenReturn(new ResponseMappingResult(true, List.of(), Map.of()));

            Map<String, Object> rule = Map.of(
                    "providerId", 5, "sourceField", "a", "targetField", "b",
                    "mappingType", "FIELD_PLACEMENT", "required", false);

            helper.applyResponseMapping(null, "{}", List.of(rule), new LinkedHashMap<>());

            ArgumentCaptor<List<ResponseFieldRuleData>> captor = ArgumentCaptor.forClass(List.class);
            verify(responseMappingEngine).apply(captor.capture(), anyMap());
            assertThat(captor.getValue()).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Conversion des règles (toRuleData)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rule conversion")
    class RuleConversion {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("skips a rule with an unsupported mappingType instead of failing")
        void applyResponseMapping_unsupportedMappingType_isFilteredOut() throws Exception {
            when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                    .thenReturn(Map.of());
            when(responseMappingEngine.apply(anyList(), anyMap()))
                    .thenReturn(new ResponseMappingResult(true, List.of(), Map.of()));

            Map<String, Object> badRule = Map.of(
                    "providerId", 1, "sourceField", "a", "targetField", "b",
                    "mappingType", "NOT_A_REAL_TYPE", "required", false);

            helper.applyResponseMapping(1L, "{}", List.of(badRule), new LinkedHashMap<>());

            ArgumentCaptor<List<ResponseFieldRuleData>> captor = ArgumentCaptor.forClass(List.class);
            verify(responseMappingEngine).apply(captor.capture(), anyMap());
            assertThat(captor.getValue()).isEmpty();
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("defaults required to false when the 'required' key is absent from the rule")
        void applyResponseMapping_missingRequiredKey_defaultsToFalse() throws Exception {
            when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                    .thenReturn(Map.of());
            when(responseMappingEngine.apply(anyList(), anyMap()))
                    .thenReturn(new ResponseMappingResult(true, List.of(), Map.of()));

            Map<String, Object> rule = Map.of(
                    "providerId", 1, "sourceField", "a", "targetField", "b",
                    "mappingType", "FIELD_PLACEMENT");

            helper.applyResponseMapping(1L, "{}", List.of(rule), new LinkedHashMap<>());

            ArgumentCaptor<List<ResponseFieldRuleData>> captor = ArgumentCaptor.forClass(List.class);
            verify(responseMappingEngine).apply(captor.capture(), anyMap());
            assertThat(captor.getValue().get(0).required()).isFalse();
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("correctly maps sourceField, targetField, expression and required=true")
        void applyResponseMapping_fullRule_mapsAllFieldsCorrectly() throws Exception {
            when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                    .thenReturn(Map.of());
            when(responseMappingEngine.apply(anyList(), anyMap()))
                    .thenReturn(new ResponseMappingResult(true, List.of(), Map.of()));

            Map<String, Object> rule = Map.of(
                    "providerId", 1, "sourceField", "amount", "targetField", "total",
                    "mappingType", "VALUE_TRANSFORM", "expression", "round(amount)", "required", true);

            helper.applyResponseMapping(1L, "{}", List.of(rule), new LinkedHashMap<>());

            ArgumentCaptor<List<ResponseFieldRuleData>> captor = ArgumentCaptor.forClass(List.class);
            verify(responseMappingEngine).apply(captor.capture(), anyMap());
            ResponseFieldRuleData data = captor.getValue().get(0);

            assertThat(data.sourceField()).isEqualTo("amount");
            assertThat(data.targetField()).isEqualTo("total");
            assertThat(data.mappingType()).isEqualTo(MappingType.VALUE_TRANSFORM);
            assertThat(data.expression()).isEqualTo("round(amount)");
            assertThat(data.required()).isTrue();
            assertThat(data.id()).isNull(); // toujours null en mode fichier
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Résultat final
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Result population")
    class ResultPopulation {

        @Test
        @DisplayName("copies passed, violations, and mappedBody from the engine result into the output map")
        void applyResponseMapping_engineResult_populatesOutputMap() throws Exception {
            when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                    .thenReturn(Map.of("status", "OK"));
            Map<String, Object> mappedBody = Map.of("orderStatus", "OK");
            when(responseMappingEngine.apply(anyList(), anyMap()))
                    .thenReturn(new ResponseMappingResult(false, List.of("field X missing"), mappedBody));

            Map<String, Object> result = new LinkedHashMap<>();
            helper.applyResponseMapping(1L, "{\"status\":\"OK\"}", List.of(), result);

            assertThat(result.get("validationPassed")).isEqualTo(false);
            assertThat(result.get("validationErrors")).isEqualTo(List.of("field X missing"));
            assertThat(result.get("mappedBody")).isEqualTo(mappedBody);
        }

        @Test
        @DisplayName("passes the parsed sourceMap through to the engine unchanged")
        void applyResponseMapping_passesParsedSourceMapToEngine() throws Exception {
            Map<String, Object> parsed = Map.of("a", 1, "b", "two");
            when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(parsed);
            when(responseMappingEngine.apply(anyList(), anyMap()))
                    .thenReturn(new ResponseMappingResult(true, List.of(), Map.of()));

            helper.applyResponseMapping(1L, "{\"a\":1,\"b\":\"two\"}", List.of(), new LinkedHashMap<>());

            verify(responseMappingEngine).apply(anyList(), eq(parsed));
        }
    }
}