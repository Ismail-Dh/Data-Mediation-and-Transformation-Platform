package com.miniESB.engine.responsemapping;

import com.miniESB.domain.enums.MappingType;

/**
 * Représentation neutre d'une règle de mapping de réponse provider (T6),
 * indépendante de sa source (entité JPA {@code ResponseMappingRule} en base,
 * ou objet JSON chargé depuis {@code rules.json} en mode "engine").
 *
 * @see com.miniESB.engine.responsemapping.ResponseMappingEngine
 */
public record ResponseFieldRuleData(
        Long id,
        String sourceField,
        String targetField,
        MappingType mappingType,
        String expression,
        boolean required
) {
    public String label() {
        return id != null ? "id=" + id : "(" + sourceField + " → " + targetField + ")";
    }
}
