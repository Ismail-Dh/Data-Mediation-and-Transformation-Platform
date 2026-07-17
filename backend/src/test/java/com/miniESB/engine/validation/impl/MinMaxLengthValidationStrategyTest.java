package com.miniESB.engine.validation.impl;

import com.miniESB.domain.enums.RuleType;
import com.miniESB.engine.validation.ValidationRuleData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MinMaxLengthValidationStrategy — unit tests")
class MinMaxLengthValidationStrategyTest {

    private final MinMaxLengthValidationStrategy strategy = new MinMaxLengthValidationStrategy();

    @Test
    @DisplayName("supports() returns MIN_MAX_LENGTH")
    void supports() {
        assertThat(strategy.supports()).isEqualTo(RuleType.MIN_MAX_LENGTH);
    }

    @Test
    @DisplayName("value length within [min,max] passes")
    void withinRange() {
        ValidationRuleData rule = new ValidationRuleData("username", RuleType.MIN_MAX_LENGTH, "3,10");
        assertThat(strategy.validate(rule, "alice")).isEmpty();
    }

    @Test
    @DisplayName("value shorter than min fails")
    void tooShort() {
        ValidationRuleData rule = new ValidationRuleData("username", RuleType.MIN_MAX_LENGTH, "3,10");
        assertThat(strategy.validate(rule, "ab")).isPresent();
    }

    @Test
    @DisplayName("value longer than max fails")
    void tooLong() {
        ValidationRuleData rule = new ValidationRuleData("username", RuleType.MIN_MAX_LENGTH, "3,10");
        assertThat(strategy.validate(rule, "a".repeat(11))).isPresent();
    }

    @Test
    @DisplayName("missing pattern is ignored (rule skipped)")
    void missingPattern() {
        ValidationRuleData rule = new ValidationRuleData("username", RuleType.MIN_MAX_LENGTH, null);
        assertThat(strategy.validate(rule, "anything")).isEmpty();
    }

    @Test
    @DisplayName("pattern without a comma is ignored (rule skipped)")
    void patternWithoutComma() {
        ValidationRuleData rule = new ValidationRuleData("username", RuleType.MIN_MAX_LENGTH, "10");
        assertThat(strategy.validate(rule, "anything")).isEmpty();
    }

    @Test
    @DisplayName("non-numeric pattern parts are ignored (rule skipped)")
    void nonNumericPattern() {
        ValidationRuleData rule = new ValidationRuleData("username", RuleType.MIN_MAX_LENGTH, "a,b");
        assertThat(strategy.validate(rule, "anything")).isEmpty();
    }
}
