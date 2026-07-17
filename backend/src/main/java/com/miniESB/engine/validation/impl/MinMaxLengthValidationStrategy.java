package com.miniESB.engine.validation.impl;

import com.miniESB.domain.enums.RuleType;
import com.miniESB.engine.validation.ValidationRuleData;
import com.miniESB.engine.validation.ValidationStrategy;
import com.miniESB.exception.FieldViolation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Pattern attendu : "min,max" (ex: "3,30"). */
@Slf4j
@Component
public class MinMaxLengthValidationStrategy implements ValidationStrategy {

    @Override
    public RuleType supports() {
        return RuleType.MIN_MAX_LENGTH;
    }

    @Override
    public Optional<FieldViolation> validate(ValidationRuleData rule, String value) {
        String patternParam = rule.pattern();
        if (patternParam == null || !patternParam.contains(",")) {
            log.warn("MIN_MAX_LENGTH rule for field '{}' has invalid pattern: '{}'", rule.fieldName(), patternParam);
            return Optional.empty();
        }
        try {
            String[] parts = patternParam.split(",", 2);
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            int len = value.length();
            if (len < min || len > max) {
                return Optional.of(new FieldViolation(rule.fieldName(), "INVALID_FORMAT",
                        "Field '" + rule.fieldName() + "' length must be between " + min + " and " + max
                                + " (got: " + len + ")"));
            }
        } catch (NumberFormatException e) {
            log.warn("Cannot parse MIN_MAX_LENGTH pattern '{}' for field '{}'", patternParam, rule.fieldName());
        }
        return Optional.empty();
    }
}
