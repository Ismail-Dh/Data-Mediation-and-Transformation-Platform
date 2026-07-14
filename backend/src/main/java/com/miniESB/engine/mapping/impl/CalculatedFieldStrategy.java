package com.miniESB.engine.mapping.impl;

import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.mapping.ExpressionEvaluator;
import com.miniESB.engine.mapping.MappingRuleData;
import com.miniESB.engine.mapping.MappingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.miniESB.engine.mapping.PayloadPathUtils.setNestedValue;

@Slf4j
@Component
public class CalculatedFieldStrategy implements MappingStrategy {

    @Override
    public MappingType supports() {
        return MappingType.CALCULATED_FIELD;
    }

    @Override
    public void apply(MappingRuleData rule, Map<String, Object> input, Map<String, Object> output) {
        if (rule.expression() == null || rule.expression().isBlank()) {
            log.warn("CALCULATED_FIELD rule {} — expression is null or blank, skipping", rule.label());
            return;
        }

        String expr = rule.expression().trim();
        Object result;

        try {
            if (ExpressionEvaluator.isConditional(expr)) {
                result = ExpressionEvaluator.evaluateConditional(expr, input);
            } else if (ExpressionEvaluator.isAggregation(expr)) {
                result = ExpressionEvaluator.evaluateAggregation(expr, input);
            } else {
                result = ExpressionEvaluator.evaluateArithmetic(expr, input);
            }
        } catch (Exception e) {
            log.error("CALCULATED_FIELD rule {} — evaluation failed: {}", rule.label(), e.getMessage());
            return;
        }

        setNestedValue(output, rule.targetField(), result);
        log.debug("CALCULATED_FIELD → '{}' = {}", rule.targetField(), result);
    }
}
