package com.miniESB.engine.responsemapping.impl;

import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.responsemapping.ResponseFieldTransformStrategy;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Copie / renommage simple, sans transformation. */
@Component
public class FieldPlacementResponseStrategy implements ResponseFieldTransformStrategy {

    @Override
    public MappingType supports() {
        return MappingType.FIELD_PLACEMENT;
    }

    @Override
    public Object apply(Object value, String expression, Map<String, Object> source) {
        return value;
    }
}
