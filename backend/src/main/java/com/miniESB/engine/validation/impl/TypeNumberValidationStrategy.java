package com.miniESB.engine.validation.impl;

import com.miniESB.domain.enums.RuleType;
import com.miniESB.engine.validation.ValidationRuleData;
import com.miniESB.engine.validation.ValidationStrategy;
import com.miniESB.exception.FieldViolation;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
public class TypeNumberValidationStrategy implements ValidationStrategy {

    @Override
    public RuleType supports() {
        return RuleType.TYPE_NUMBER;
    }

    @Override
    public Optional<FieldViolation> validate(ValidationRuleData rule, String value) {
        // Si un pattern personnalisé est fourni, il prévaut sur la vérification par défaut
        if (rule.pattern() != null && !rule.pattern().isBlank()) {
            try {
                if (!Pattern.compile(rule.pattern()).matcher(value).matches()) {
                    return Optional.of(new FieldViolation(rule.fieldName(), "INVALID_FORMAT",
                            "Field '" + rule.fieldName() + "' does not match TYPE_NUMBER format (got: " + value + ")"));
                }
            } catch (PatternSyntaxException ignored) {
                // pattern invalide → règle ignorée, comme dans le comportement historique
            }
            return Optional.empty();
        }

        try {
            Double.parseDouble(value);
            return Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.of(new FieldViolation(rule.fieldName(), "INVALID_FORMAT",
                    "Field '" + rule.fieldName() + "' must be a valid number (got: " + value + ")"));
        }
    }
}
