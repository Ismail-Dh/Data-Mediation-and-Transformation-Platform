package com.miniESB.engine.mapping;

import com.miniESB.domain.enums.MappingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MappingEngine — unit tests")
class MappingEngineTest {

    private MappingEngine engine;

    /** Simple stub strategy: copies sourceField's value to targetField. */
    private static class StubFieldPlacement implements MappingStrategy {
        @Override
        public MappingType supports() {
            return MappingType.FIELD_PLACEMENT;
        }

        @Override
        public void apply(MappingRuleData rule, Map<String, Object> input, Map<String, Object> output) {
            output.remove(rule.sourceField());
            output.put(rule.targetField(), input.get(rule.sourceField()));
        }
    }

    private static class ThrowingStrategy implements MappingStrategy {
        @Override
        public MappingType supports() {
            return MappingType.CALCULATED_FIELD;
        }

        @Override
        public void apply(MappingRuleData rule, Map<String, Object> input, Map<String, Object> output) {
            throw new RuntimeException("boom");
        }
    }

    @BeforeEach
    void setUp() {
        engine = new MappingEngine(List.of(new StubFieldPlacement(), new ThrowingStrategy()));
    }

    @Test
    @DisplayName("returns the input unchanged when no rules are provided")
    void noRules() {
        Map<String, Object> input = new HashMap<>(Map.of("a", 1));
        Map<String, Object> result = engine.apply(List.of(), input);
        assertThat(result).isSameAs(input);
    }

    @Test
    @DisplayName("dispatches to the matching strategy for a supported mapping type")
    void dispatchesToMatchingStrategy() {
        Map<String, Object> input = new HashMap<>(Map.of("firstName", "Alice"));
        MappingRuleData rule = new MappingRuleData(1L, "firstName", "givenName", MappingType.FIELD_PLACEMENT, null);

        Map<String, Object> result = engine.apply(List.of(rule), input);

        assertThat(result).containsEntry("givenName", "Alice").doesNotContainKey("firstName");
    }

    @Test
    @DisplayName("skips rules whose mapping type has no registered strategy")
    void skipsUnsupportedType() {
        Map<String, Object> input = new HashMap<>(Map.of("a", 1));
        MappingRuleData rule = new MappingRuleData(1L, "a", "b", MappingType.RESTRUCTURING, null);

        Map<String, Object> result = engine.apply(List.of(rule), input);

        // RESTRUCTURING has no stub strategy registered here -> input passthrough for that field
        assertThat(result).containsEntry("a", 1);
    }

    @Test
    @DisplayName("continues processing remaining rules when one strategy throws")
    void continuesAfterStrategyFailure() {
        Map<String, Object> input = new HashMap<>(Map.of("firstName", "Alice", "x", 1));
        MappingRuleData failingRule = new MappingRuleData(1L, "x", "y", MappingType.CALCULATED_FIELD, "boom-expr");
        MappingRuleData okRule = new MappingRuleData(2L, "firstName", "givenName", MappingType.FIELD_PLACEMENT, null);

        Map<String, Object> result = engine.apply(List.of(failingRule, okRule), input);

        assertThat(result).containsEntry("givenName", "Alice");
    }
}
