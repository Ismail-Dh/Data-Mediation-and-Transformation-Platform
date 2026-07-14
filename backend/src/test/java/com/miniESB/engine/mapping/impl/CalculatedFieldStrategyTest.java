package com.miniESB.engine.mapping.impl;

import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.mapping.MappingRuleData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CalculatedFieldStrategy — unit tests")
class CalculatedFieldStrategyTest {

    private final CalculatedFieldStrategy strategy = new CalculatedFieldStrategy();
    private Map<String, Object> input;
    private Map<String, Object> output;

    @BeforeEach
    void setUp() {
        input = new HashMap<>(Map.of("price", 100, "tva", 0.2));
        output = new HashMap<>(input);
    }

    @Test
    @DisplayName("supports() returns CALCULATED_FIELD")
    void supports() {
        assertThat(strategy.supports()).isEqualTo(MappingType.CALCULATED_FIELD);
    }

    @Test
    @DisplayName("evaluates an arithmetic expression and stores the result on targetField")
    void arithmetic() {
        MappingRuleData rule = new MappingRuleData(1L, null, "totalWithTax",
                MappingType.CALCULATED_FIELD, "{price} * (1+{tva})");

        strategy.apply(rule, input, output);

        assertThat((Double) output.get("totalWithTax")).isEqualTo(120.0, within(0.0001));
    }

    @Test
    @DisplayName("evaluates a conditional (IF:) expression")
    void conditional() {
        Map<String, Object> in = new HashMap<>(Map.of("age", 20));
        Map<String, Object> out = new HashMap<>(in);
        MappingRuleData rule = new MappingRuleData(1L, null, "category",
                MappingType.CALCULATED_FIELD, "IF:age:gt:18:adult:minor");

        strategy.apply(rule, in, out);

        assertThat(out).containsEntry("category", "adult");
    }

    @Test
    @DisplayName("evaluates an aggregation (SUM:) expression")
    void aggregation() {
        Map<String, Object> in = new HashMap<>(Map.of("items",
                java.util.List.of(Map.of("price", 10), Map.of("price", 20))));
        Map<String, Object> out = new HashMap<>(in);
        MappingRuleData rule = new MappingRuleData(1L, null, "total",
                MappingType.CALCULATED_FIELD, "SUM:items[].price");

        strategy.apply(rule, in, out);

        assertThat((Double) out.get("total")).isEqualTo(30.0, within(0.0001));
    }

    @Test
    @DisplayName("skips silently when the expression is null or blank")
    void blankExpression() {
        MappingRuleData rule = new MappingRuleData(1L, null, "total", MappingType.CALCULATED_FIELD, "  ");

        strategy.apply(rule, input, output);

        assertThat(output).doesNotContainKey("total");
    }

    @Test
    @DisplayName("swallows evaluation failures and leaves the output untouched")
    void evaluationFailureIsSwallowed() {
        MappingRuleData rule = new MappingRuleData(1L, null, "total",
                MappingType.CALCULATED_FIELD, "{missingField} + 1");

        assertThatCode(() -> strategy.apply(rule, input, output)).doesNotThrowAnyException();
        assertThat(output).doesNotContainKey("total");
    }
}
