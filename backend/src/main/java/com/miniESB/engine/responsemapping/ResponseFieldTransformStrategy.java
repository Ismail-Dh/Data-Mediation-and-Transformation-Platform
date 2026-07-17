package com.miniESB.engine.responsemapping;

import com.miniESB.domain.enums.MappingType;

import java.util.Map;

/**
 * Une stratégie de transformation pour une règle de mapping de réponse provider (T6),
 * pour un {@link MappingType} donné.
 *
 * <p>Remplace le {@code switch (rule.getMappingType())} qui était dupliqué entre
 * {@code ResponseMappingServiceImpl} (mode base de données) et
 * {@code EngineResponseMappingHelper} (mode fichier) — violation OCP + DRY.
 * Notez que la sémantique T6 diffère volontairement de celle du mapping T5
 * (package {@code engine.mapping}) : ici FORMAT_CHANGE ne supporte que
 * UPPERCASE/LOWERCASE/TRIM et CALCULATED_FIELD réalise une concaténation —
 * ce sont des choix historiques du module T6, préservés tels quels.</p>
 */
public interface ResponseFieldTransformStrategy {

    MappingType supports();

    /**
     * @param value      valeur brute déjà résolue (jamais {@code null})
     * @param expression expression de la règle (peut être {@code null}/vide)
     * @param source     corps source complet (nécessaire pour CALCULATED_FIELD/concat)
     */
    Object apply(Object value, String expression, Map<String, Object> source);
}
