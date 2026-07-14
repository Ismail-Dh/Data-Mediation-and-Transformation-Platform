package com.miniESB.engine.validation;

import com.miniESB.domain.enums.RuleType;
import com.miniESB.exception.FieldViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidationEngine — unit tests")
class ValidationEngineTest {

    @Mock
    private FieldResolver resolver;

    private ValidationEngine engine;

    private static class StubStrategy implements ValidationStrategy {
        private final RuleType type;
        private final Optional<FieldViolation> result;

        StubStrategy(RuleType type, Optional<FieldViolation> result) {
            this.type = type;
            this.result = result;
        }

        @Override
        public RuleType supports() {
            return type;
        }

        @Override
        public Optional<FieldViolation> validate(ValidationRuleData rule, String value) {
            return result;
        }
    }

    @BeforeEach
    void setUp() {
        engine = new ValidationEngine(List.of(
                new StubStrategy(RuleType.TYPE_NUMBER, Optional.empty()),
                new StubStrategy(RuleType.REGEX, Optional.of(
                        new FieldViolation("field", "INVALID_FORMAT", "bad")))
        ));
    }

    @Test
    @DisplayName("NOT_NULL rule adds a violation when the field is absent/null")
    void notNullMissingField() {
        when(resolver.isPresentAndNotNull("email")).thenReturn(false);
        ValidationRuleData rule = new ValidationRuleData("email", RuleType.NOT_NULL, null);

        List<FieldViolation> violations = engine.validate(List.of(rule), resolver);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).fieldPath()).isEqualTo("email");
    }

    @Test
    @DisplayName("NOT_NULL rule adds no violation when the field is present")
    void notNullPresentField() {
        when(resolver.isPresentAndNotNull("email")).thenReturn(true);
        ValidationRuleData rule = new ValidationRuleData("email", RuleType.NOT_NULL, null);

        List<FieldViolation> violations = engine.validate(List.of(rule), resolver);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("skips a non-NOT_NULL rule entirely when the field is absent")
    void skipsAbsentFieldForOtherRules() {
        when(resolver.isPresentAndNotNull("age")).thenReturn(false);
        ValidationRuleData rule = new ValidationRuleData("age", RuleType.TYPE_NUMBER, null);

        List<FieldViolation> violations = engine.validate(List.of(rule), resolver);

        assertThat(violations).isEmpty();
        verify(resolver, never()).resolveAsText("age");
    }

    @Test
    @DisplayName("delegates to the matching strategy when the field is present, collecting its violation")
    void delegatesToMatchingStrategy() {
        when(resolver.isPresentAndNotNull("code")).thenReturn(true);
        when(resolver.resolveAsText("code")).thenReturn("abc");
        ValidationRuleData rule = new ValidationRuleData("code", RuleType.REGEX, "[0-9]+");

        List<FieldViolation> violations = engine.validate(List.of(rule), resolver);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).fieldPath()).isEqualTo("field");
    }

    @Test
    @DisplayName("logs and skips when no strategy is registered for the rule type")
    void unknownStrategyIsSkipped() {
        when(resolver.isPresentAndNotNull("phone")).thenReturn(true);
        when(resolver.resolveAsText("phone")).thenReturn("123");
        ValidationRuleData rule = new ValidationRuleData("phone", RuleType.REGEX_PHONE, null);

        List<FieldViolation> violations = engine.validate(List.of(rule), resolver);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("returns an empty list when given no rules")
    void noRules() {
        assertThat(engine.validate(List.of(), resolver)).isEmpty();
    }
}
