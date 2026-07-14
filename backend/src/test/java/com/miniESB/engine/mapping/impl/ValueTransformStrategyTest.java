package com.miniESB.engine.mapping.impl;

import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.mapping.MappingRuleData;
import com.miniESB.engine.mapping.operator.LowercaseOperator;
import com.miniESB.engine.mapping.operator.RegexReplaceOperator;
import com.miniESB.engine.mapping.operator.SplitOperator;
import com.miniESB.engine.mapping.operator.TrimOperator;
import com.miniESB.engine.mapping.operator.UppercaseOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ValueTransformStrategy — unit tests")
class ValueTransformStrategyTest {

    private ValueTransformStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ValueTransformStrategy(List.of(
                new LowercaseOperator(), new UppercaseOperator(), new TrimOperator(),
                new SplitOperator(), new RegexReplaceOperator()));
    }

    @Test
    @DisplayName("supports() returns VALUE_TRANSFORM")
    void supports() {
        assertThat(strategy.supports()).isEqualTo(MappingType.VALUE_TRANSFORM);
    }

    @Test
    @DisplayName("applies a matching operator, moving the value from source to target field")
    void appliesMatchingOperator() {
        Map<String, Object> input = new HashMap<>(Map.of("name", "Alice"));
        Map<String, Object> output = new HashMap<>(input);
        MappingRuleData rule = new MappingRuleData(1L, "name", "nameUpper", MappingType.VALUE_TRANSFORM, "UPPERCASE");

        strategy.apply(rule, input, output);

        assertThat(output).containsEntry("nameUpper", "ALICE").doesNotContainKey("name");
    }

    @Test
    @DisplayName("CONCAT expression joins several input fields with a separator")
    void concat() {
        Map<String, Object> input = new HashMap<>(Map.of("first", "Alice", "last", "Doe"));
        Map<String, Object> output = new HashMap<>(input);
        MappingRuleData rule = new MappingRuleData(1L, "first", "fullName",
                MappingType.VALUE_TRANSFORM, "CONCAT: :first:last");

        strategy.apply(rule, input, output);

        assertThat(output).containsEntry("fullName", "Alice Doe");
    }

    @Test
    @DisplayName("throws internally (caught) when CONCAT references a missing field — output untouched")
    void concatMissingFieldIsSwallowed() {
        Map<String, Object> input = new HashMap<>(Map.of("first", "Alice"));
        Map<String, Object> output = new HashMap<>(input);
        MappingRuleData rule = new MappingRuleData(1L, null, "fullName",
                MappingType.VALUE_TRANSFORM, "CONCAT: :first:last");

        assertThatCode(() -> strategy.apply(rule, input, output)).doesNotThrowAnyException();
        assertThat(output).doesNotContainKey("fullName");
    }

    @Test
    @DisplayName("skips when the expression is null or blank")
    void blankExpression() {
        Map<String, Object> input = new HashMap<>(Map.of("name", "Alice"));
        Map<String, Object> output = new HashMap<>(input);
        MappingRuleData rule = new MappingRuleData(1L, "name", "name2", MappingType.VALUE_TRANSFORM, "");

        strategy.apply(rule, input, output);

        assertThat(output).doesNotContainKey("name2");
    }

    @Test
    @DisplayName("skips when the sourceField is missing from input")
    void missingSourceField() {
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> output = new HashMap<>();
        MappingRuleData rule = new MappingRuleData(1L, "missing", "target", MappingType.VALUE_TRANSFORM, "UPPERCASE");

        strategy.apply(rule, input, output);

        assertThat(output).doesNotContainKey("target");
    }

    @Test
    @DisplayName("swallows failure when no operator supports the expression")
    void noOperatorSupports() {
        Map<String, Object> input = new HashMap<>(Map.of("name", "Alice"));
        Map<String, Object> output = new HashMap<>(input);
        MappingRuleData rule = new MappingRuleData(1L, "name", "target", MappingType.VALUE_TRANSFORM, "UNKNOWN_OP");

        assertThatCode(() -> strategy.apply(rule, input, output)).doesNotThrowAnyException();
        assertThat(output).doesNotContainKey("target");
    }
}
