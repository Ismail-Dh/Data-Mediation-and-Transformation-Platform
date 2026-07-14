package com.miniESB.engine.validation.impl;

import com.miniESB.domain.enums.RuleType;
import com.miniESB.engine.validation.ValidationRuleData;
import com.miniESB.exception.FieldViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TypeNumberValidationStrategy — unit tests")
class TypeNumberValidationStrategyTest {

    private final TypeNumberValidationStrategy strategy = new TypeNumberValidationStrategy();

    @Test
    @DisplayName("supports() returns TYPE_NUMBER")
    void supports() {
        assertThat(strategy.supports()).isEqualTo(RuleType.TYPE_NUMBER);
    }

    @Test
    @DisplayName("valid numeric string passes without a custom pattern")
    void validNumber() {
        ValidationRuleData rule = new ValidationRuleData("age", RuleType.TYPE_NUMBER, null);
        assertThat(strategy.validate(rule, "42.5")).isEmpty();
    }

    @Test
    @DisplayName("non-numeric string fails without a custom pattern")
    void invalidNumber() {
        ValidationRuleData rule = new ValidationRuleData("age", RuleType.TYPE_NUMBER, null);
        Optional<FieldViolation> result = strategy.validate(rule, "abc");
        assertThat(result).isPresent();
        assertThat(result.get().errorType()).isEqualTo("INVALID_FORMAT");
    }

    @Test
    @DisplayName("a custom pattern overrides the default numeric check and is enforced")
    void customPatternEnforced() {
        ValidationRuleData rule = new ValidationRuleData("code", RuleType.TYPE_NUMBER, "[0-9]{4}");
        assertThat(strategy.validate(rule, "1234")).isEmpty();
        assertThat(strategy.validate(rule, "12")).isPresent();
    }

    @Test
    @DisplayName("an invalid custom regex pattern is ignored (rule skipped)")
    void invalidCustomPatternIgnored() {
        ValidationRuleData rule = new ValidationRuleData("code", RuleType.TYPE_NUMBER, "[");
        assertThat(strategy.validate(rule, "anything")).isEmpty();
    }
}
