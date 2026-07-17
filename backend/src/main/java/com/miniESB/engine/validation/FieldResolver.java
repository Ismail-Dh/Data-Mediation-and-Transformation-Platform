package com.miniESB.engine.validation;

/**
 * Résout la valeur d'un champ (dot-notation) dans un payload, quelle que soit
 * sa représentation interne. Permet à {@link ValidationEngine} d'être utilisé
 * aussi bien avec un {@code JsonNode} (Jackson, utilisé par
 * {@code BusinessValidatorService}) qu'avec une {@code Map<String,Object>}
 * (utilisée par {@code EngineProcessService} en mode fichier) — sans dupliquer
 * la logique de validation elle-même pour chaque représentation.
 *
 * @see JsonNodeFieldResolver
 * @see MapFieldResolver
 */
public interface FieldResolver {

    /** @return la valeur textuelle du champ, ou {@code null} si absent/null. */
    String resolveAsText(String path);

    /** @return {@code true} si le champ existe et n'est pas explicitement null. */
    boolean isPresentAndNotNull(String path);
}
