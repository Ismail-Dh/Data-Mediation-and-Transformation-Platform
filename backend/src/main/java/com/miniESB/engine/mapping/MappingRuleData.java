package com.miniESB.engine.mapping;

import com.miniESB.domain.enums.MappingType;

/**
 * Représentation neutre d'une règle de mapping, indépendante de sa source
 * (entité JPA {@code MappingRule} en base, ou objet JSON chargé depuis un
 * fichier {@code rules.json} en mode "engine").
 *
 * <p>C'est cette abstraction qui permet à {@link MappingEngine} d'être
 * partagé entre le mode base de données (MappingServiceImpl) et le mode
 * fichier (EngineProcessService) — corrige la duplication de code qui
 * existait entre ces deux classes (violation DRY/SRP).</p>
 *
 * @param id           identifiant de la règle (peut être {@code null} en mode fichier)
 * @param sourceField  chemin du champ source (notation pointée, ex: "customer.email")
 * @param targetField  chemin du champ cible
 * @param mappingType  type de mapping (FIELD_PLACEMENT, VALUE_TRANSFORM, ...)
 * @param expression   expression associée au type (peut être {@code null})
 */
public record MappingRuleData(
        Long id,
        String sourceField,
        String targetField,
        MappingType mappingType,
        String expression
) {
    /** Identifiant lisible utilisé dans les logs quand la règle n'a pas d'id (mode fichier). */
    public String label() {
        return id != null ? "id=" + id : "(" + sourceField + " → " + targetField + ")";
    }
}
