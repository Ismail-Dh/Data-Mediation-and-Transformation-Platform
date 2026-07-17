package com.miniESB.engine.responsemapping.impl;

import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.responsemapping.ResponseFieldTransformStrategy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * FORMAT_CHANGE : l'expression indique l'opération de formatage à appliquer
 * (UPPERCASE, LOWERCASE, TRIM). Sous-ensemble volontairement restreint par
 * rapport à {@code engine.mapping.impl.FormatChangeStrategy} (T5) — comportement
 * historique du module T6, préservé tel quel.
 */
@Component
public class FormatChangeResponseStrategy implements ResponseFieldTransformStrategy {

    @Override
    public MappingType supports() {
        return MappingType.FORMAT_CHANGE;
    }

    @Override
    public Object apply(Object value, String expression, Map<String, Object> source) {
        if (expression == null || expression.isBlank()) return value;
        return switch (expression.trim().toUpperCase()) {
            case "UPPERCASE" -> value.toString().toUpperCase();
            case "LOWERCASE" -> value.toString().toLowerCase();
            case "TRIM" -> value.toString().trim();
            default -> throw new IllegalArgumentException("Unknown FORMAT_CHANGE expression: " + expression);
        };
    }
}
