package com.miniESB.engine.validation.impl;

import com.miniESB.domain.enums.RuleType;
import com.miniESB.engine.validation.ValidationRuleData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TypeDateValidationStrategy — unit tests")
class TypeDateValidationStrategyTest {

    private final TypeDateValidationStrategy strategy = new TypeDateValidationStrategy();

    @Test
    @DisplayName("supports() returns TYPE_DATE")
    void supports() {
        assertThat(strategy.supports()).isEqualTo(RuleType.TYPE_DATE);
    }

    @Test
    @DisplayName("valid ISO-8601 date passes with the default pattern")
    void validIsoDate() {
        ValidationRuleData rule = new ValidationRuleData("dob", RuleType.TYPE_DATE, null);
        assertThat(strategy.validate(rule, "2024-01-15")).isEmpty();
    }

    @Test
    @DisplayName("invalid date fails with the default pattern")
    void invalidIsoDate() {
        ValidationRuleData rule = new ValidationRuleData("dob", RuleType.TYPE_DATE, null);
        assertThat(strategy.validate(rule, "not-a-date")).isPresent();
    }

    @Test
    @DisplayName("a custom date pattern is honored")
    void customPattern() {
        ValidationRuleData rule = new ValidationRuleData("dob", RuleType.TYPE_DATE, "dd/MM/yyyy");
        assertThat(strategy.validate(rule, "25/12/2024")).isEmpty();
        assertThat(strategy.validate(rule, "2024-12-25")).isPresent();
    }
}
