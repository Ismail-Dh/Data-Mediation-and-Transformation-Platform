package com.miniESB.engine.mapping.impl;

import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.mapping.MappingRuleData;
import com.miniESB.engine.mapping.MappingStrategy;
import com.miniESB.engine.mapping.ValueTransformOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.miniESB.engine.mapping.PayloadPathUtils.*;

@Slf4j
@Component
public class ValueTransformStrategy implements MappingStrategy {

    private final List<ValueTransformOperator> operators;

    public ValueTransformStrategy(List<ValueTransformOperator> operators) {
        this.operators = operators;
    }

    @Override
    public MappingType supports() {
        return MappingType.VALUE_TRANSFORM;
    }

    @Override
    public void apply(MappingRuleData rule, Map<String, Object> input, Map<String, Object> output) {
        if (rule.expression() == null || rule.expression().isBlank()) {
            log.warn("VALUE_TRANSFORM rule {} — expression is null or blank, skipping", rule.label());
            return;
        }

        String expr = rule.expression().trim();
        Object transformed;

        try {
            if (expr.startsWith("CONCAT:")) {
                // CONCAT a besoin de plusieurs champs du payload : ce n'est pas un
                // ValueTransformOperator "classique" (rawValue, expression) mais une
                // opération sur l'input complet — traitée séparément par conception.
                transformed = applyConcat(expr, input);
            } else {
                Object raw = getNestedValue(input, rule.sourceField());
                if (raw == null) {
                    log.warn("VALUE_TRANSFORM rule {} — sourceField '{}' not found in input",
                            rule.label(), rule.sourceField());
                    return;
                }
                transformed = transformSingleValue(raw, expr);
            }
        } catch (Exception e) {
            log.error("VALUE_TRANSFORM rule {} — failed: {}", rule.label(), e.getMessage());
            return;
        }

        removeNestedKey(output, rule.sourceField());
        setNestedValue(output, rule.targetField(), transformed);
        log.debug("VALUE_TRANSFORM '{}' → '{}' ({})", rule.sourceField(), rule.targetField(), expr);
    }

    private Object transformSingleValue(Object raw, String expr) {
        return operators.stream()
                .filter(op -> op.supports(expr))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown VALUE_TRANSFORM expression: " + expr))
                .apply(raw, expr);
    }

    /** expression = "CONCAT:separator:field1:field2:..." */
    private Object applyConcat(String expr, Map<String, Object> input) {
        String[] parts = expr.split(":", -1);
        if (parts.length < 4)
            throw new IllegalArgumentException("CONCAT format: CONCAT:separator:field1:field2[:field3...]");

        String separator = parts[1];
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < parts.length; i++) {
            Object val = getNestedValue(input, parts[i].trim());
            if (val == null)
                throw new IllegalArgumentException("CONCAT — field '" + parts[i].trim() + "' not found in input");
            if (i > 2) sb.append(separator);
            sb.append(val);
        }
        return sb.toString();
    }
}
