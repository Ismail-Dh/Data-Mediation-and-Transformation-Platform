package com.miniESB.engine.responsemapping.impl;

import com.miniESB.domain.enums.MappingType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Response field transform strategies — unit tests")
class ResponseFieldTransformStrategiesTest {

    @Nested
    @DisplayName("RestructuringResponseStrategy")
    class Restructuring {
        private final RestructuringResponseStrategy strategy = new RestructuringResponseStrategy();

        @Test
        @DisplayName("supports() returns RESTRUCTURING and apply() passes the value through unchanged")
        void passthrough() {
            assertThat(strategy.supports()).isEqualTo(MappingType.RESTRUCTURING);
            assertThat(strategy.apply("value", "anything", Map.of())).isEqualTo("value");
        }
    }

    @Nested
    @DisplayName("FormatChangeResponseStrategy")
    class FormatChange {
        private final FormatChangeResponseStrategy strategy = new FormatChangeResponseStrategy();

        @Test
        @DisplayName("supports() returns FORMAT_CHANGE")
        void supports() {
            assertThat(strategy.supports()).isEqualTo(MappingType.FORMAT_CHANGE);
        }

        @Test
        @DisplayName("UPPERCASE/LOWERCASE/TRIM expressions transform the value")
        void transforms() {
            assertThat(strategy.apply("Hello", "UPPERCASE", Map.of())).isEqualTo("HELLO");
            assertThat(strategy.apply("Hello", "LOWERCASE", Map.of())).isEqualTo("hello");
            assertThat(strategy.apply(" Hello ", "TRIM", Map.of())).isEqualTo("Hello");
        }

        @Test
        @DisplayName("blank expression returns the value unchanged")
        void blankExpression() {
            assertThat(strategy.apply("Hello", "", Map.of())).isEqualTo("Hello");
            assertThat(strategy.apply("Hello", null, Map.of())).isEqualTo("Hello");
        }

        @Test
        @DisplayName("unknown expression throws")
        void unknownExpression() {
            assertThatThrownBy(() -> strategy.apply("Hello", "REVERSE", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("CalculatedFieldResponseStrategy")
    class CalculatedField {
        private final CalculatedFieldResponseStrategy strategy = new CalculatedFieldResponseStrategy();

        @Test
        @DisplayName("supports() returns CALCULATED_FIELD")
        void supports() {
            assertThat(strategy.supports()).isEqualTo(MappingType.CALCULATED_FIELD);
        }

        @Test
        @DisplayName("concatenates fields listed in the '+' separated expression")
        void concatenatesFields() {
            Map<String, Object> source = Map.of("firstName", "Alice", "lastName", "Doe");
            Object result = strategy.apply("ignored", "firstName+' '+lastName", source);
            assertThat(result).isEqualTo("Alice Doe");
        }

        @Test
        @DisplayName("returns empty string for a blank expression")
        void blankExpression() {
            assertThat(strategy.apply("ignored", "", Map.of())).isEqualTo("");
            assertThat(strategy.apply("ignored", null, Map.of())).isEqualTo("");
        }

        @Test
        @DisplayName("falls back to the literal token when it is not found in source")
        void unknownTokenFallsBackToLiteral() {
            Object result = strategy.apply("ignored", "unknownToken", Map.of());
            assertThat(result).isEqualTo("unknownToken");
        }
    }

    @Nested
    @DisplayName("ValueTransformResponseStrategy")
    class ValueTransform {
        private final ValueTransformResponseStrategy strategy = new ValueTransformResponseStrategy();

        @Test
        @DisplayName("supports() returns VALUE_TRANSFORM")
        void supports() {
            assertThat(strategy.supports()).isEqualTo(MappingType.VALUE_TRANSFORM);
        }

        @Test
        @DisplayName("CONSTANT: expression returns the fixed literal, ignoring the value")
        void constantExpression() {
            Object result = strategy.apply("ignored", "CONSTANT:fixedValue", Map.of());
            assertThat(result).isEqualTo("fixedValue");
        }

        @Test
        @DisplayName("blank or non-constant expression returns the value unchanged")
        void passthrough() {
            assertThat(strategy.apply("value", "", Map.of())).isEqualTo("value");
            assertThat(strategy.apply("value", null, Map.of())).isEqualTo("value");
            assertThat(strategy.apply("value", "SOMETHING_ELSE", Map.of())).isEqualTo("value");
        }
    }

    @Nested
    @DisplayName("FieldPlacementResponseStrategy")
    class FieldPlacement {
        private final FieldPlacementResponseStrategy strategy = new FieldPlacementResponseStrategy();

        @Test
        @DisplayName("supports() returns FIELD_PLACEMENT and apply() passes the value through unchanged")
        void passthrough() {
            assertThat(strategy.supports()).isEqualTo(MappingType.FIELD_PLACEMENT);
            assertThat(strategy.apply(42, "anything", Map.of())).isEqualTo(42);
        }
    }
}
