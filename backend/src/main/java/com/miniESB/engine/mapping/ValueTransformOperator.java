package com.miniESB.engine.mapping;

/**
 * Un opérateur de transformation de valeur pour les règles {@code VALUE_TRANSFORM}
 * (ex : UPPERCASE, SPLIT:...:..., REGEX_REPLACE:...:...).
 *
 * <p>Ajouter un nouvel opérateur = ajouter une implémentation de cette interface
 * et l'enregistrer dans {@link MappingEngine} — aucune classe existante n'a besoin
 * d'être modifiée (Open/Closed Principle). Avant ce refactoring, chaque nouvel
 * opérateur nécessitait d'ajouter un {@code if} dans {@code transformSingleValue()}.</p>
 */
public interface ValueTransformOperator {

    /**
     * @param expression expression complète de la règle (ex: "SPLIT:,:0"), déjà trim().
     * @return {@code true} si cet opérateur sait traiter cette expression.
     */
    boolean supports(String expression);

    /**
     * Applique la transformation à la valeur brute.
     *
     * @param rawValue   valeur source (déjà résolue depuis le payload)
     * @param expression expression complète de la règle
     */
    Object apply(Object rawValue, String expression);
}
