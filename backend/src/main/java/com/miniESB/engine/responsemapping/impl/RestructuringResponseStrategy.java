package com.miniESB.engine.responsemapping.impl;

import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.responsemapping.ResponseFieldTransformStrategy;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Pas de restructuration imbriquée pour les réponses — comportement historique T6. */
@Component
public class RestructuringResponseStrategy implements ResponseFieldTransformStrategy {

    @Override
    public MappingType supports() {
        return MappingType.RESTRUCTURING;
    }

    @Override
    public Object apply(Object value, String expression, Map<String, Object> source) {
        return value;
    }
}
