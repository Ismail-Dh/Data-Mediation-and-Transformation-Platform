package com.miniESB.engine.responsemapping;

import com.miniESB.domain.enums.MappingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ResponseMappingEngine — unit tests")
class ResponseMappingEngineTest {

    private ResponseMappingEngine engine;

    private static class UppercaseStrategy implements ResponseFieldTransformStrategy {
        @Override
        public MappingType supports() {
            return MappingType.FORMAT_CHANGE;
        }

        @Override
        public Object apply(Object value, String expression, Map<String, Object> source) {
            return value.toString().toUpperCase();
        }
    }

    private static class ThrowingStrategy implements ResponseFieldTransformStrategy {
        @Override
        public MappingType supports() {
            return MappingType.CALCULATED_FIELD;
        }

        @Override
        public Object apply(Object value, String expression, Map<String, Object> source) {
            throw new RuntimeException("boom");
        }
    }

    @BeforeEach
    void setUp() {
        engine = new ResponseMappingEngine(List.of(new UppercaseStrategy(), new ThrowingStrategy()));
    }

    @Test
    @DisplayName("applies the matching strategy and maps the field to targetField")
    void appliesMatchingStrategy() {
        Map<String, Object> source = Map.of("status", "ok");
        ResponseFieldRuleData rule = new ResponseFieldRuleData(1L, "status", "statusUpper",
                MappingType.FORMAT_CHANGE, null, false);

        ResponseMappingResult result = engine.apply(List.of(rule), source);

        assertThat(result.passed()).isTrue();
        assertThat(result.mappedBody()).containsEntry("statusUpper", "OK");
    }

    @Test
    @DisplayName("adds a violation when a required field is missing, result not passed")
    void requiredFieldMissing() {
        Map<String, Object> source = Map.of();
        ResponseFieldRuleData rule = new ResponseFieldRuleData(1L, "id", "id",
                MappingType.FORMAT_CHANGE, null, true);

        ResponseMappingResult result = engine.apply(List.of(rule), source);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).hasSize(1);
    }

    @Test
    @DisplayName("silently skips a missing non-required field")
    void optionalFieldMissing() {
        Map<String, Object> source = Map.of();
        ResponseFieldRuleData rule = new ResponseFieldRuleData(1L, "id", "id",
                MappingType.FORMAT_CHANGE, null, false);

        ResponseMappingResult result = engine.apply(List.of(rule), source);

        assertThat(result.passed()).isTrue();
        assertThat(result.mappedBody()).doesNotContainKey("id");
    }

    @Test
    @DisplayName("records a violation when the transformation strategy throws")
    void transformationFailure() {
        Map<String, Object> source = Map.of("x", 1);
        ResponseFieldRuleData rule = new ResponseFieldRuleData(1L, "x", "y",
                MappingType.CALCULATED_FIELD, null, false);

        ResponseMappingResult result = engine.apply(List.of(rule), source);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations().get(0)).contains("x");
    }

    @Test
    @DisplayName("passes through fields not covered by any rule")
    void passthroughUncoveredFields() {
        Map<String, Object> source = Map.of("status", "ok", "extra", "value");
        ResponseFieldRuleData rule = new ResponseFieldRuleData(1L, "status", "statusUpper",
                MappingType.FORMAT_CHANGE, null, false);

        ResponseMappingResult result = engine.apply(List.of(rule), source);

        assertThat(result.mappedBody()).containsEntry("extra", "value").containsEntry("status", "ok");
    }

    @Test
    @DisplayName("skips fields whose mapping type has no registered strategy")
    void unsupportedMappingType() {
        Map<String, Object> source = Map.of("a", "b");
        ResponseFieldRuleData rule = new ResponseFieldRuleData(1L, "a", "a2",
                MappingType.RESTRUCTURING, null, false);

        ResponseMappingResult result = engine.apply(List.of(rule), source);

        assertThat(result.passed()).isTrue();
        assertThat(result.mappedBody()).doesNotContainKey("a2");
    }
}
