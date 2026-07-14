package com.miniESB.engine.responsemapping.impl;

import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.responsemapping.ResponseFieldTransformStrategy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * VALUE_TRANSFORM : si l'expression commence par "CONSTANT:", retourne une
 * valeur fixe indépendante du champ source ; sinon retourne la valeur telle
 * quelle. Comportement historique T6, préservé tel quel.
 */
@Component
public class ValueTransformResponseStrategy implements ResponseFieldTransformStrategy {

    private static final String CONSTANT_PREFIX = "CONSTANT:";

    @Override
    public MappingType supports() {
        return MappingType.VALUE_TRANSFORM;
    }

    @Override
    public Object apply(Object value, String expression, Map<String, Object> source) {
        if (expression == null || expression.isBlank()) return value;
        if (expression.startsWith(CONSTANT_PREFIX)) {
            return expression.substring(CONSTANT_PREFIX.length());
        }
        return value;
    }
}
