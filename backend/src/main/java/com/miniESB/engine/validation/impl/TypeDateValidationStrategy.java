package com.miniESB.engine.validation.impl;

import com.miniESB.domain.enums.RuleType;
import com.miniESB.engine.validation.ValidationRuleData;
import com.miniESB.engine.validation.ValidationStrategy;
import com.miniESB.exception.FieldViolation;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Component
public class TypeDateValidationStrategy implements ValidationStrategy {

    @Override
    public RuleType supports() {
        return RuleType.TYPE_DATE;
    }

    @Override
    public Optional<FieldViolation> validate(ValidationRuleData rule, String value) {
        boolean valid;
        try {
            if (rule.pattern() != null && !rule.pattern().isBlank()) {
                LocalDate.parse(value, DateTimeFormatter.ofPattern(rule.pattern()));
            } else {
                LocalDate.parse(value); // ISO-8601 par défaut : YYYY-MM-DD
            }
            valid = true;
        } catch (Exception e) {
            valid = false;
        }

        if (valid) return Optional.empty();

        return Optional.of(new FieldViolation(rule.fieldName(), "INVALID_FORMAT",
                "Field '" + rule.fieldName() + "' must be a valid date in ISO-8601 format YYYY-MM-DD (got: " + value + ")"));
    }
}
