package com.miniESB.engine.mapping.impl;

import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.mapping.MappingRuleData;
import com.miniESB.engine.mapping.MappingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.miniESB.engine.mapping.PayloadPathUtils.*;

@Slf4j
@Component
public class RestructuringStrategy implements MappingStrategy {

    @Override
    public MappingType supports() {
        return MappingType.RESTRUCTURING;
    }

    @Override
    public void apply(MappingRuleData rule, Map<String, Object> input, Map<String, Object> output) {
        String expr = rule.expression() == null ? "" : rule.expression().trim().toUpperCase();

        if (expr.equals("FLATTEN")) {
            applyFlatten(rule, input, output);
        } else {
            applyNest(rule, input, output);
        }
    }

    private void applyNest(MappingRuleData rule, Map<String, Object> input, Map<String, Object> output) {
        Object value = getNestedValue(input, rule.sourceField());
        if (value == null) {
            log.warn("RESTRUCTURING/NEST rule {} — sourceField '{}' not found in input",
                    rule.label(), rule.sourceField());
            return;
        }

        removeNestedKey(output, rule.sourceField());
        setNestedValue(output, rule.targetField(), value);
        log.debug("RESTRUCTURING/NEST '{}' → '{}'", rule.sourceField(), rule.targetField());
    }

    @SuppressWarnings("unchecked")
    private void applyFlatten(MappingRuleData rule, Map<String, Object> input, Map<String, Object> output) {
        Object subObj = getNestedValue(input, rule.sourceField());

        if (!(subObj instanceof Map)) {
            log.warn("RESTRUCTURING/FLATTEN rule {} — '{}' is not an object (or not found)",
                    rule.label(), rule.sourceField());
            return;
        }

        Map<String, Object> subMap = (Map<String, Object>) subObj;
        String prefix = rule.sourceField();

        flattenInto(output, subMap, prefix);
        removeNestedKey(output, rule.sourceField());

        log.debug("RESTRUCTURING/FLATTEN '{}' → {} clés aplaties", rule.sourceField(), subMap.size());
    }
}
