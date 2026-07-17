package com.miniESB.engine.mapping.impl;

import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.mapping.MappingRuleData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FieldPlacementStrategy — unit tests")
class FieldPlacementStrategyTest {

    private final FieldPlacementStrategy strategy = new FieldPlacementStrategy();

    @Test
    @DisplayName("supports() returns FIELD_PLACEMENT")
    void supports() {
        assertThat(strategy.supports()).isEqualTo(MappingType.FIELD_PLACEMENT);
    }

    @Test
    @DisplayName("moves a simple top-level field from source to target")
    void simpleFieldMove() {
        Map<String, Object> input = new HashMap<>(Map.of("firstName", "Alice"));
        Map<String, Object> output = new HashMap<>(input);
        MappingRuleData rule = new MappingRuleData(1L, "firstName", "givenName", MappingType.FIELD_PLACEMENT, null);

        strategy.apply(rule, input, output);

        assertThat(output).containsEntry("givenName", "Alice").doesNotContainKey("firstName");
    }

    @Test
    @DisplayName("skips silently when the sourceField is missing")
    void missingSourceField() {
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> output = new HashMap<>();
        MappingRuleData rule = new MappingRuleData(1L, "missing", "target", MappingType.FIELD_PLACEMENT, null);

        strategy.apply(rule, input, output);

        assertThat(output).doesNotContainKey("target");
    }

    @Test
    @DisplayName("maps an array sub-field to a renamed array sub-field")
    void arrayFieldMapping() {
        List<Object> items = new ArrayList<>(List.of(
                new HashMap<>(Map.of("price", 10)),
                new HashMap<>(Map.of("price", 20))));
        Map<String, Object> input = new HashMap<>(Map.of("items", items));
        Map<String, Object> output = new HashMap<>(input);

        MappingRuleData rule = new MappingRuleData(1L, "items[].price", "items[].amount",
                MappingType.FIELD_PLACEMENT, null);

        strategy.apply(rule, input, output);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outItems = (List<Map<String, Object>>) output.get("items");
        assertThat(outItems).hasSize(2);
        assertThat(outItems.get(0)).containsEntry("amount", 10).doesNotContainKey("price");
        assertThat(outItems.get(1)).containsEntry("amount", 20);
    }

    @Test
    @DisplayName("skips silently when the array path does not resolve to a list")
    void arrayPathNotAList() {
        Map<String, Object> input = new HashMap<>(Map.of("items", "not-a-list"));
        Map<String, Object> output = new HashMap<>(input);
        MappingRuleData rule = new MappingRuleData(1L, "items[].price", "items[].amount",
                MappingType.FIELD_PLACEMENT, null);

        assertThatCode(() -> strategy.apply(rule, input, output)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("skips items missing the sub-field, still processing the others")
    void arrayItemMissingSubField() {
        List<Object> items = new ArrayList<>(List.of(
                new HashMap<>(Map.of("other", 1)),
                new HashMap<>(Map.of("price", 20))));
        Map<String, Object> input = new HashMap<>(Map.of("items", items));
        Map<String, Object> output = new HashMap<>(input);

        MappingRuleData rule = new MappingRuleData(1L, "items[].price", "items[].amount",
                MappingType.FIELD_PLACEMENT, null);

        strategy.apply(rule, input, output);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outItems = (List<Map<String, Object>>) output.get("items");
        assertThat(outItems.get(0)).doesNotContainKey("amount");
        assertThat(outItems.get(1)).containsEntry("amount", 20);
    }
}
