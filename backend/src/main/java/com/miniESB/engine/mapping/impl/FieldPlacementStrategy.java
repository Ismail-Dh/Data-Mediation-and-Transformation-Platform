package com.miniESB.engine.mapping.impl;

import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.mapping.MappingRuleData;
import com.miniESB.engine.mapping.MappingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.miniESB.engine.mapping.PayloadPathUtils.*;

@Slf4j
@Component
public class FieldPlacementStrategy implements MappingStrategy {

    @Override
    public MappingType supports() {
        return MappingType.FIELD_PLACEMENT;
    }

    @Override
    public void apply(MappingRuleData rule, Map<String, Object> input, Map<String, Object> output) {
        String src = rule.sourceField();
        String tgt = rule.targetField();

        if (src.contains("[].")) {
            processArrayField(rule, input, output);
            return;
        }

        Object value = getNestedValue(input, src);
        if (value == null) {
            log.warn("FIELD_PLACEMENT rule {} — sourceField '{}' not found in input", rule.label(), src);
            return;
        }

        removeNestedKey(output, src);
        setNestedValue(output, tgt, value);
        log.debug("Mapped '{}' → '{}'", src, tgt);
    }

    @SuppressWarnings("unchecked")
    private void processArrayField(MappingRuleData rule, Map<String, Object> input, Map<String, Object> output) {
        String src = rule.sourceField();
        String tgt = rule.targetField();

        int sep = src.indexOf("[].");
        String arrayPath = src.substring(0, sep);
        String subSrc = src.substring(sep + 3);

        String subTgt = tgt.contains("[].") ? tgt.substring(tgt.indexOf("[].") + 3) : tgt;
        String tgtArray = tgt.contains("[].") ? tgt.substring(0, tgt.indexOf("[].")) : arrayPath;

        Object arrayObj = getNestedValue(input, arrayPath);
        if (!(arrayObj instanceof List)) {
            log.warn("FIELD_PLACEMENT rule {} — '{}' is not an array in input", rule.label(), arrayPath);
            return;
        }

        List<Object> items = (List<Object>) arrayObj;
        List<Object> outItems = (List<Object>) output.computeIfAbsent(tgtArray, k -> new ArrayList<>());

        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof Map)) continue;
            Map<String, Object> srcItem = (Map<String, Object>) items.get(i);

            while (outItems.size() <= i) outItems.add(new HashMap<String, Object>());
            Map<String, Object> outItem = (Map<String, Object>) outItems.get(i);

            Object value = getNestedValue(srcItem, subSrc);
            if (value == null) {
                log.warn("FIELD_PLACEMENT rule {} — '{}' missing in item[{}]", rule.label(), subSrc, i);
                continue;
            }
            outItem.remove(subSrc);
            setNestedValue(outItem, subTgt, value);
        }
    }
}
