package com.miniESB.engine.responsemapping;

import java.util.List;
import java.util.Map;

/**
 * @param passed     {@code true} si aucune violation (champ requis absent ou transformation échouée)
 * @param violations messages de violation (vide si {@code passed})
 * @param mappedBody corps de réponse transformé, avec passthrough des champs non couverts par une règle
 */
public record ResponseMappingResult(boolean passed, List<String> violations, Map<String, Object> mappedBody) {
}
