package com.miniESB.engine.validation.impl;

import com.miniESB.domain.enums.RuleType;
import com.miniESB.engine.validation.ValidationRuleData;
import com.miniESB.engine.validation.ValidationStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Regex-pattern based validation strategies — unit tests")
class RegexPatternValidationStrategiesTest {

    private static final String EMAIL_PATTERN = "^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$";
    private static final String PHONE_PATTERN = "^\\+?[0-9]{7,15}$";

    @Nested
    @DisplayName("RegexEmailValidationStrategy")
    class Email {
        private final RegexEmailValidationStrategy strategy = new RegexEmailValidationStrategy();

        @Test
        @DisplayName("supports() returns REGEX_EMAIL")
        void supports() {
            assertThat(strategy.supports()).isEqualTo(RuleType.REGEX_EMAIL);
        }

        @Test
        @DisplayName("valid email matches the provided pattern")
        void validEmail() {
            ValidationRuleData rule = new ValidationRuleData("email", RuleType.REGEX_EMAIL, EMAIL_PATTERN);
            assertThat(strategy.validate(rule, "alice@example.com")).isEmpty();
        }

        @Test
        @DisplayName("invalid email fails against the provided pattern")
        void invalidEmail() {
            ValidationRuleData rule = new ValidationRuleData("email", RuleType.REGEX_EMAIL, EMAIL_PATTERN);
            assertThat(strategy.validate(rule, "not-an-email")).isPresent();
        }

        @Test
        @DisplayName("missing pattern is skipped with no violation")
        void missingPattern() {
            ValidationRuleData rule = new ValidationRuleData("email", RuleType.REGEX_EMAIL, null);
            assertThat(strategy.validate(rule, "whatever")).isEmpty();
        }
    }

    @Nested
    @DisplayName("RegexPhoneValidationStrategy")
    class Phone {
        private final RegexPhoneValidationStrategy strategy = new RegexPhoneValidationStrategy();

        @Test
        @DisplayName("supports() returns REGEX_PHONE")
        void supports() {
            assertThat(strategy.supports()).isEqualTo(RuleType.REGEX_PHONE);
        }

        @Test
        @DisplayName("valid phone number matches the provided pattern")
        void validPhone() {
            ValidationRuleData rule = new ValidationRuleData("phone", RuleType.REGEX_PHONE, PHONE_PATTERN);
            assertThat(strategy.validate(rule, "+33612345678")).isEmpty();
        }

        @Test
        @DisplayName("invalid phone number fails against the provided pattern")
        void invalidPhone() {
            ValidationRuleData rule = new ValidationRuleData("phone", RuleType.REGEX_PHONE, PHONE_PATTERN);
            assertThat(strategy.validate(rule, "abc")).isPresent();
        }
    }

    @Nested
    @DisplayName("RegexPasswordValidationStrategy")
    class Password {
        private final RegexPasswordValidationStrategy strategy = new RegexPasswordValidationStrategy();
        private static final String PASSWORD_PATTERN = "^(?=.*[A-Z])(?=.*[0-9]).{8,}$";

        @Test
        @DisplayName("supports() returns REGEX_PASSWORD")
        void supports() {
            assertThat(strategy.supports()).isEqualTo(RuleType.REGEX_PASSWORD);
        }

        @Test
        @DisplayName("strong password matches the provided pattern")
        void strongPassword() {
            ValidationRuleData rule = new ValidationRuleData("password", RuleType.REGEX_PASSWORD, PASSWORD_PATTERN);
            assertThat(strategy.validate(rule, "Str0ngPass")).isEmpty();
        }

        @Test
        @DisplayName("weak password fails against the provided pattern")
        void weakPassword() {
            ValidationRuleData rule = new ValidationRuleData("password", RuleType.REGEX_PASSWORD, PASSWORD_PATTERN);
            assertThat(strategy.validate(rule, "weak")).isPresent();
        }
    }

    @Nested
    @DisplayName("RegexValidationStrategy (free-form)")
    class Generic {
        private final RegexValidationStrategy strategy = new RegexValidationStrategy();

        @Test
        @DisplayName("supports() returns REGEX")
        void supports() {
            assertThat(strategy.supports()).isEqualTo(RuleType.REGEX);
        }

        @Test
        @DisplayName("matches a custom free-form pattern")
        void customPattern() {
            ValidationRuleData rule = new ValidationRuleData("code", RuleType.REGEX, "^[A-Z]{3}-[0-9]{4}$");
            assertThat(strategy.validate(rule, "ABC-1234")).isEmpty();
            assertThat(strategy.validate(rule, "abc-1234")).isPresent();
        }

        @Test
        @DisplayName("invalid regex syntax is caught and treated as a skip (no violation, no exception)")
        void invalidRegexSyntax() {
            ValidationRuleData rule = new ValidationRuleData("code", RuleType.REGEX, "[unclosed");
            ValidationStrategy s = strategy; // exercise via interface too
            assertThatCode(() -> s.validate(rule, "anything")).doesNotThrowAnyException();
            assertThat(strategy.validate(rule, "anything")).isEmpty();
        }
    }
}
