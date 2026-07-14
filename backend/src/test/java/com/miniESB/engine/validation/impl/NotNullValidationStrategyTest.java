package com.miniESB.engine.validation.impl;

import com.miniESB.domain.enums.RuleType;
import com.miniESB.engine.validation.ValidationRuleData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("NotNullValidationStrategy — unit tests")
class NotNullValidationStrategyTest {

    private final NotNullValidationStrategy strategy = new NotNullValidationStrategy();

    @Test
    @DisplayName("supports() returns NOT_NULL")
    void supports() {
        assertThat(strategy.supports()).isEqualTo(RuleType.NOT_NULL);
    }

    @Test
    @DisplayName("validate() is a no-op that always returns empty (presence is handled by ValidationEngine)")
    void alwaysEmpty() {
        ValidationRuleData rule = new ValidationRuleData("field", RuleType.NOT_NULL, null);
        assertThat(strategy.validate(rule, "any-value")).isEmpty();
        assertThat(strategy.validate(rule, null)).isEmpty();
    }
}
