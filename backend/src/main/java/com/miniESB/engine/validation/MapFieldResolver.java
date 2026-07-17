package com.miniESB.engine.validation;

import java.util.Map;

import static com.miniESB.engine.mapping.PayloadPathUtils.getNestedValue;

/** Adapte une {@code Map<String,Object>} (payload JSON déjà parsé) en {@link FieldResolver}. */
public class MapFieldResolver implements FieldResolver {

    private final Map<String, Object> root;

    public MapFieldResolver(Map<String, Object> root) {
        this.root = root;
    }

    @Override
    public String resolveAsText(String path) {
        Object value = getNestedValue(root, path);
        return value == null ? null : value.toString();
    }

    @Override
    public boolean isPresentAndNotNull(String path) {
        return getNestedValue(root, path) != null;
    }
}
