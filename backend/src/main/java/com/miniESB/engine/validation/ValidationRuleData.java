package com.miniESB.engine.validation;

import com.miniESB.domain.enums.RuleType;

/**
 * Représentation neutre d'une règle de validation niveau-2, indépendante de sa
 * source (entité JPA {@code ValidationRule}/{@code GlobalValidationRule} en base,
 * ou objet JSON chargé depuis {@code rules.json} en mode "engine").
 *
 * @param fieldName chemin du champ ciblé (dot-notation)
 * @param ruleType  type de règle (NOT_NULL, REGEX_EMAIL, ...)
 * @param pattern   pattern associé (regex, "min,max"...) — peut être {@code null}
 */
public record ValidationRuleData(String fieldName, RuleType ruleType, String pattern) {
}
