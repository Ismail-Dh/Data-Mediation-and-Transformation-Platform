package com.miniESB.engine.mapping.impl;

import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.mapping.MappingRuleData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RestructuringStrategy — unit tests")
class RestructuringStrategyTest {

    private final RestructuringStrategy strategy = new RestructuringStrategy();

    @Test
    @DisplayName("supports() returns RESTRUCTURING")
    void supports() {
        assertThat(strategy.supports()).isEqualTo(MappingType.RESTRUCTURING);
    }

    @Test
    @DisplayName("NEST (default) moves a value from source to target path")
    void nest() {
        Map<String, Object> input = new HashMap<>(Map.of("city", "Paris"));
        Map<String, Object> output = new HashMap<>(input);
        MappingRuleData rule = new MappingRuleData(1L, "city", "address.city", MappingType.RESTRUCTURING, null);

        strategy.apply(rule, input, output);

        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) output.get("address");
        assertThat(address).containsEntry("city", "Paris");
        assertThat(output).doesNotContainKey("city");
    }

    @Test
    @DisplayName("NEST skips silently when the sourceField is missing")
    void nestMissingSource() {
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> output = new HashMap<>();
        MappingRuleData rule = new MappingRuleData(1L, "missing", "address.city", MappingType.RESTRUCTURING, null);

        strategy.apply(rule, input, output);

        assertThat(output).doesNotContainKey("address");
    }

    @Test
    @DisplayName("FLATTEN expands a sub-object's keys with the source path as prefix")
    void flatten() {
        Map<String, Object> address = new HashMap<>(Map.of("city", "Paris", "zip", "75000"));
        Map<String, Object> input = new HashMap<>(Map.of("address", address));
        Map<String, Object> output = new HashMap<>(input);
        MappingRuleData rule = new MappingRuleData(1L, "address", null, MappingType.RESTRUCTURING, "FLATTEN");

        strategy.apply(rule, input, output);

        assertThat(output).containsEntry("address.city", "Paris").containsEntry("address.zip", "75000")
                .doesNotContainKey("address");
    }

    @Test
    @DisplayName("FLATTEN skips silently when the sourceField is not an object")
    void flattenNotAnObject() {
        Map<String, Object> input = new HashMap<>(Map.of("address", "not-an-object"));
        Map<String, Object> output = new HashMap<>(input);
        MappingRuleData rule = new MappingRuleData(1L, "address", null, MappingType.RESTRUCTURING, "FLATTEN");

        assertThatCode(() -> strategy.apply(rule, input, output)).doesNotThrowAnyException();
        assertThat(output).containsEntry("address", "not-an-object");
    }
}
