package com.miniESB.serviceImpl;

import com.miniESB.exception.FieldViolation;
import com.miniESB.exception.PayloadValidationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
@DisplayName("Exception classes — unit tests")
class ExceptionClassesTest {

    @Nested
    @DisplayName("FieldViolation")
    class FieldViolationTest {

        @Test
        @DisplayName("stores all three fields correctly")
        void fieldViolation_storesFields() {
            FieldViolation violation = new FieldViolation(
                    "order.id", "MISSING_FIELD", "Field is required");

            assertThat(violation.fieldPath()).isEqualTo("order.id");
            assertThat(violation.errorType()).isEqualTo("MISSING_FIELD");
            assertThat(violation.message()).isEqualTo("Field is required");
        }

        @Test
        @DisplayName("supports all defined error types")
        void fieldViolation_allErrorTypes() {
            assertThatCode(() -> new FieldViolation("f", "MISSING_FIELD",    "msg")).doesNotThrowAnyException();
            assertThatCode(() -> new FieldViolation("f", "TYPE_MISMATCH",    "msg")).doesNotThrowAnyException();
            assertThatCode(() -> new FieldViolation("f", "NULL_NOT_ALLOWED", "msg")).doesNotThrowAnyException();
            assertThatCode(() -> new FieldViolation("f", "INVALID_FORMAT",   "msg")).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("PayloadValidationException")
    class PayloadValidationExceptionTest {

        @Test
        @DisplayName("stores violations and has correct message")
        void payloadValidationException_storesViolations() {
            List<FieldViolation> violations = List.of(
                    new FieldViolation("orderId", "MISSING_FIELD", "Required"),
                    new FieldViolation("email",   "TYPE_MISMATCH", "Expected string"));

            PayloadValidationException ex = new PayloadValidationException(violations);

            assertThat(ex.getViolations()).hasSize(2);
            assertThat(ex.getViolations().get(0).fieldPath()).isEqualTo("orderId");
            assertThat(ex.getMessage()).isEqualTo("Payload structural validation failed");
        }

        @Test
        @DisplayName("works with an empty violation list")
        void payloadValidationException_emptyViolations() {
            PayloadValidationException ex = new PayloadValidationException(List.of());

            assertThat(ex.getViolations()).isEmpty();
        }

        @Test
        @DisplayName("is a RuntimeException")
        void payloadValidationException_isRuntime() {
            assertThat(new PayloadValidationException(List.of()))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("TemplateImmutableException")
    class TemplateImmutableExceptionTest {

        @Test
        @DisplayName("message contains id and status")
        void templateImmutableException_message() {
            com.miniESB.exception.TemplateImmutableException ex =
                    new com.miniESB.exception.TemplateImmutableException(42L, "PUBLISHED");

            assertThat(ex.getMessage()).contains("42");
            assertThat(ex.getMessage()).contains("PUBLISHED");
        }

        @Test
        @DisplayName("works for DISABLED status")
        void templateImmutableException_disabled() {
            com.miniESB.exception.TemplateImmutableException ex =
                    new com.miniESB.exception.TemplateImmutableException(7L, "DISABLED");

            assertThat(ex.getMessage()).contains("DISABLED");
            assertThat(ex.getMessage()).contains("7");
        }

        @Test
        @DisplayName("is a RuntimeException")
        void templateImmutableException_isRuntime() {
            assertThat(new com.miniESB.exception.TemplateImmutableException(1L, "PUBLISHED"))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}