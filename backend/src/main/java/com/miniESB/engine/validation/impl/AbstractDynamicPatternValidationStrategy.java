package com.miniESB.engine.validation.impl;

import com.miniESB.engine.validation.ValidationRuleData;
import com.miniESB.engine.validation.ValidationStrategy;
import com.miniESB.exception.FieldViolation;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Base commune à REGEX_EMAIL, REGEX_PHONE, REGEX_PASSWORD et REGEX : toutes
 * valident la valeur contre un pattern regex stocké dans {@code rule.pattern()}.
 *
 * <p>Par conception, aucun pattern par défaut n'est codé en dur ici — le pattern
 * doit être fourni par la règle (comportement documenté de {@code BusinessValidatorService}
 * historique). Si absent, la règle est ignorée avec un avertissement plutôt que
 * de silencieusement appliquer un regex différent selon le mode DB/fichier.</p>
 */
@Slf4j
abstract class AbstractDynamicPatternValidationStrategy implements ValidationStrategy {

    protected abstract String ruleLabel();

    @Override
    public Optional<FieldViolation> validate(ValidationRuleData rule, String value) {
        String patternStr = rule.pattern();
        if (patternStr == null || patternStr.isBlank()) {
            log.warn("{} rule for field '{}' has no pattern defined — skipping", ruleLabel(), rule.fieldName());
            return Optional.empty();
        }
        try {
            Pattern compiled = Pattern.compile(patternStr);
            if (!compiled.matcher(value).matches()) {
                return Optional.of(new FieldViolation(rule.fieldName(), "INVALID_FORMAT",
                        "Field '" + rule.fieldName() + "' does not match " + ruleLabel()
                                + " format (got: " + value + ")"));
            }
        } catch (PatternSyntaxException e) {
            log.warn("{} rule for field '{}' has invalid regex pattern '{}': {}",
                    ruleLabel(), rule.fieldName(), patternStr, e.getMessage());
        }
        return Optional.empty();
    }
}
