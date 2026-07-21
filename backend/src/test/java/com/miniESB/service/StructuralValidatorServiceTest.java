package com.miniESB.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.PipelineField;
import com.miniESB.domain.enums.FieldType;
import com.miniESB.exception.FieldViolation;
import com.miniESB.exception.PayloadValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitaires pour {@link StructuralValidatorService}.
 *
 * <p>Pas de mock : {@code ObjectMapper} est instancié réellement, le service ne fait
 * que du parsing JSON pur — mocker readTree n'apporterait rien.</p>
 */
@DisplayName("StructuralValidatorService")
class StructuralValidatorServiceTest {

    private StructuralValidatorService service;

    @BeforeEach
    void setUp() {
        service = new StructuralValidatorService(new ObjectMapper());
    }

    private PipelineField field(String path, FieldType type, boolean required, boolean nullable) {
        return PipelineField.builder()
                .fieldPath(path)
                .fieldType(type)
                .required(required)
                .nullable(nullable)
                .build();
    }

    private List<FieldViolation> violationsOf(Runnable validation) {
        try {
            validation.run();
            return List.of();
        } catch (PayloadValidationException e) {
            return e.getViolations();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  JSON malformé
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("invalid JSON")
    class InvalidJson {

        @Test
        @DisplayName("throws with a single INVALID_JSON violation when rawContent is not parseable")
        void malformedJson_throwsInvalidJson() {
            String raw = "{ not: valid json";
            List<PipelineField> fields = List.of(field("orderId", FieldType.STRING, true, false));

            List<FieldViolation> violations = violationsOf(() -> service.validate(raw, fields));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).errorType()).isEqualTo("INVALID_JSON");
            assertThat(violations.get(0).fieldPath()).isEqualTo("$");
        }

        @Test
        @DisplayName("throws PayloadValidationException (not a generic exception)")
        void malformedJson_throwsCorrectExceptionType() {
            String raw = "not json at all";
            List<PipelineField> fields = List.of(field("orderId", FieldType.STRING, true, false));

            assertThatThrownBy(() -> service.validate(raw, fields))
                    .isInstanceOf(PayloadValidationException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Champs simples (non-tableau)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("simple fields")
    class SimpleFields {

        @Test
        @DisplayName("passes with no violations when all fields match their type")
        void validPayload_noViolations() {
            String raw = """
                {"orderId": "ORD-1", "quantity": 5, "active": true,
                 "customer": {"email": "a@b.com"}, "tags": ["a","b"]}
                """;
            List<PipelineField> fields = List.of(
                    field("orderId", FieldType.STRING, true, false),
                    field("quantity", FieldType.INTEGER, true, false),
                    field("active", FieldType.BOOLEAN, true, false),
                    field("customer", FieldType.OBJECT, true, false),
                    field("customer.email", FieldType.STRING, true, false),
                    field("tags", FieldType.ARRAY, true, false)
            );

            assertThatCode(() -> service.validate(raw, fields)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("adds MISSING_FIELD when a required field is absent")
        void missingRequiredField_addsViolation() {
            String raw = "{}";
            List<PipelineField> fields = List.of(field("orderId", FieldType.STRING, true, false));

            List<FieldViolation> violations = violationsOf(() -> service.validate(raw, fields));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).errorType()).isEqualTo("MISSING_FIELD");
            assertThat(violations.get(0).fieldPath()).isEqualTo("orderId");
        }

        @Test
        @DisplayName("does not add a violation when an optional field is absent")
        void missingOptionalField_noViolation() {
            String raw = "{}";
            List<PipelineField> fields = List.of(field("orderId", FieldType.STRING, false, false));

            assertThatCode(() -> service.validate(raw, fields)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("adds NULL_NOT_ALLOWED when field is null and not nullable")
        void nullValue_notNullable_addsViolation() {
            String raw = "{\"orderId\": null}";
            List<PipelineField> fields = List.of(field("orderId", FieldType.STRING, true, false));

            List<FieldViolation> violations = violationsOf(() -> service.validate(raw, fields));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).errorType()).isEqualTo("NULL_NOT_ALLOWED");
        }

        @Test
        @DisplayName("accepts a null value when the field is nullable")
        void nullValue_nullable_noViolation() {
            String raw = "{\"orderId\": null}";
            List<PipelineField> fields = List.of(field("orderId", FieldType.STRING, true, true));

            assertThatCode(() -> service.validate(raw, fields)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("adds TYPE_MISMATCH when the value's type doesn't match the expected type")
        void wrongType_addsTypeMismatch() {
            String raw = "{\"quantity\": \"five\"}";
            List<PipelineField> fields = List.of(field("quantity", FieldType.INTEGER, true, false));

            List<FieldViolation> violations = violationsOf(() -> service.validate(raw, fields));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).errorType()).isEqualTo("TYPE_MISMATCH");
            assertThat(violations.get(0).message()).contains("STRING");
        }

        @Test
        @DisplayName("resolves nested dotted paths correctly, e.g. customer.email")
        void nestedPath_resolvesCorrectly() {
            String raw = "{\"customer\": {\"email\": 123}}";
            List<PipelineField> fields = List.of(field("customer.email", FieldType.STRING, true, false));

            List<FieldViolation> violations = violationsOf(() -> service.validate(raw, fields));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).fieldPath()).isEqualTo("customer.email");
            assertThat(violations.get(0).errorType()).isEqualTo("TYPE_MISMATCH");
        }

        @Test
        @DisplayName("treats a missing intermediate parent as a missing field, not an error")
        void missingIntermediateParent_treatedAsMissing() {
            String raw = "{}";
            List<PipelineField> fields = List.of(field("customer.email", FieldType.STRING, true, false));

            List<FieldViolation> violations = violationsOf(() -> service.validate(raw, fields));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).errorType()).isEqualTo("MISSING_FIELD");
        }

        @Test
        @DisplayName("collects multiple violations across multiple fields, not just the first")
        void multipleFields_collectsAllViolations() {
            String raw = "{\"a\": null}";
            List<PipelineField> fields = List.of(
                    field("a", FieldType.STRING, true, false),
                    field("b", FieldType.STRING, true, false)
            );

            List<FieldViolation> violations = violationsOf(() -> service.validate(raw, fields));

            assertThat(violations).hasSize(2);
            assertThat(violations).extracting(FieldViolation::errorType)
                    .containsExactlyInAnyOrder("NULL_NOT_ALLOWED", "MISSING_FIELD");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Champs tableau ("items[].qty")
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("array fields")
    class ArrayFields {

        @Test
        @DisplayName("passes when every array item satisfies the sub-field constraints")
        void allItemsValid_noViolations() {
            String raw = "{\"items\": [{\"qty\": 1}, {\"qty\": 2}]}";
            List<PipelineField> fields = List.of(field("items[].qty", FieldType.INTEGER, true, false));

            assertThatCode(() -> service.validate(raw, fields)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("adds MISSING_FIELD on the array path when required array is absent")
        void requiredArrayMissing_addsViolation() {
            String raw = "{}";
            List<PipelineField> fields = List.of(field("items[].qty", FieldType.INTEGER, true, false));

            List<FieldViolation> violations = violationsOf(() -> service.validate(raw, fields));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).errorType()).isEqualTo("MISSING_FIELD");
            assertThat(violations.get(0).fieldPath()).isEqualTo("items");
        }

        @Test
        @DisplayName("adds no violation when an optional array is absent")
        void optionalArrayMissing_noViolation() {
            String raw = "{}";
            List<PipelineField> fields = List.of(field("items[].qty", FieldType.INTEGER, false, false));

            assertThatCode(() -> service.validate(raw, fields)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("adds TYPE_MISMATCH on the array path when the field is not actually an array")
        void arrayPathNotAnArray_addsTypeMismatch() {
            String raw = "{\"items\": \"not-an-array\"}";
            List<PipelineField> fields = List.of(field("items[].qty", FieldType.INTEGER, true, false));

            List<FieldViolation> violations = violationsOf(() -> service.validate(raw, fields));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).errorType()).isEqualTo("TYPE_MISMATCH");
            assertThat(violations.get(0).fieldPath()).isEqualTo("items");
        }

        @Test
        @DisplayName("adds MISSING_FIELD with the indexed path when a required sub-field is absent on one item")
        void missingRequiredSubField_addsIndexedViolation() {
            String raw = "{\"items\": [{\"qty\": 1}, {}]}";
            List<PipelineField> fields = List.of(field("items[].qty", FieldType.INTEGER, true, false));

            List<FieldViolation> violations = violationsOf(() -> service.validate(raw, fields));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).errorType()).isEqualTo("MISSING_FIELD");
            assertThat(violations.get(0).fieldPath()).isEqualTo("items[1].qty");
        }

        @Test
        @DisplayName("adds NULL_NOT_ALLOWED with the indexed path when a sub-field is null and not nullable")
        void nullSubField_notNullable_addsIndexedViolation() {
            String raw = "{\"items\": [{\"qty\": null}]}";
            List<PipelineField> fields = List.of(field("items[].qty", FieldType.INTEGER, true, false));

            List<FieldViolation> violations = violationsOf(() -> service.validate(raw, fields));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).errorType()).isEqualTo("NULL_NOT_ALLOWED");
            assertThat(violations.get(0).fieldPath()).isEqualTo("items[0].qty");
        }

        @Test
        @DisplayName("accepts a null sub-field when the field is nullable")
        void nullSubField_nullable_noViolation() {
            String raw = "{\"items\": [{\"qty\": null}]}";
            List<PipelineField> fields = List.of(field("items[].qty", FieldType.INTEGER, true, true));

            assertThatCode(() -> service.validate(raw, fields)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("adds TYPE_MISMATCH with the indexed path when a sub-field has the wrong type")
        void wrongTypeSubField_addsIndexedTypeMismatch() {
            String raw = "{\"items\": [{\"qty\": \"two\"}]}";
            List<PipelineField> fields = List.of(field("items[].qty", FieldType.INTEGER, true, false));

            List<FieldViolation> violations = violationsOf(() -> service.validate(raw, fields));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).errorType()).isEqualTo("TYPE_MISMATCH");
            assertThat(violations.get(0).fieldPath()).isEqualTo("items[0].qty");
        }

        @Test
        @DisplayName("checks every item in the array, not just the first")
        void multipleItems_checksEachOne() {
            String raw = "{\"items\": [{\"qty\": 1}, {\"qty\": \"bad\"}, {}]}";
            List<PipelineField> fields = List.of(field("items[].qty", FieldType.INTEGER, true, false));

            List<FieldViolation> violations = violationsOf(() -> service.validate(raw, fields));

            assertThat(violations).hasSize(2);
            assertThat(violations).extracting(FieldViolation::fieldPath)
                    .containsExactlyInAnyOrder("items[1].qty", "items[2].qty");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  checkType — toutes les branches de FieldType
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("checkType — all FieldType branches")
    class CheckTypeBranches {

        @Test
        @DisplayName("STRING accepts textual nodes")
        void string_accepted() {
            assertNoViolation("{\"v\": \"hello\"}", FieldType.STRING);
        }

        @Test
        @DisplayName("INTEGER accepts both int and long nodes")
        void integer_accepted() {
            assertNoViolation("{\"v\": 42}", FieldType.INTEGER);
            assertNoViolation("{\"v\": 9999999999}", FieldType.INTEGER);
        }

        @Test
        @DisplayName("BOOLEAN accepts boolean nodes")
        void boolean_accepted() {
            assertNoViolation("{\"v\": true}", FieldType.BOOLEAN);
        }

        @Test
        @DisplayName("OBJECT accepts object nodes")
        void object_accepted() {
            assertNoViolation("{\"v\": {\"a\": 1}}", FieldType.OBJECT);
        }

        @Test
        @DisplayName("ARRAY accepts array nodes")
        void array_accepted() {
            assertNoViolation("{\"v\": [1,2]}", FieldType.ARRAY);
        }

        @Test
        @DisplayName("TYPE_MISMATCH message reports NUMBER as the actual type for a decimal value")
        void decimalValue_reportedAsNumber() {
            String raw = "{\"v\": 3.14}";
            List<PipelineField> fields = List.of(field("v", FieldType.STRING, true, false));

            List<FieldViolation> violations = violationsOf(() -> service.validate(raw, fields));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).message()).contains("NUMBER");
        }

        private void assertNoViolation(String raw, FieldType type) {
            List<PipelineField> fields = List.of(field("v", type, true, false));
            assertThatCode(() -> service.validate(raw, fields)).doesNotThrowAnyException();
        }
    }
}