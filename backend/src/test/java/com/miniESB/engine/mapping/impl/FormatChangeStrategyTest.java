package com.miniESB.engine.mapping.impl;

import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.mapping.MappingRuleData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FormatChangeStrategy — unit tests")
class FormatChangeStrategyTest {

    private final FormatChangeStrategy strategy = new FormatChangeStrategy();

    @Test
    @DisplayName("supports() returns FORMAT_CHANGE")
    void supports() {
        assertThat(strategy.supports()).isEqualTo(MappingType.FORMAT_CHANGE);
    }

    private Map<String, Object> apply(Object rawValue, String expression, String source, String target) {
        Map<String, Object> input = new HashMap<>(Map.of(source, rawValue));
        Map<String, Object> output = new HashMap<>(input);
        MappingRuleData rule = new MappingRuleData(1L, source, target, MappingType.FORMAT_CHANGE, expression);
        strategy.apply(rule, input, output);
        return output;
    }

    @Nested
    @DisplayName("primitive conversions")
    class Primitives {

        @Test
        @DisplayName("STRING_TO_INT parses a numeric string, including decimals")
        void stringToInt() {
            Map<String, Object> out = apply("3.0", "STRING_TO_INT", "src", "tgt");
            assertThat(out).containsEntry("tgt", 3);
        }

        @Test
        @DisplayName("STRING_TO_DOUBLE parses a decimal string")
        void stringToDouble() {
            Map<String, Object> out = apply("3.5", "STRING_TO_DOUBLE", "src", "tgt");
            assertThat(out).containsEntry("tgt", 3.5);
        }

        @Test
        @DisplayName("STRING_TO_BOOL accepts common truthy/falsy tokens")
        void stringToBool() {
            assertThat(apply("true", "STRING_TO_BOOL", "src", "tgt")).containsEntry("tgt", true);
            assertThat(apply("oui", "STRING_TO_BOOL", "src", "tgt")).containsEntry("tgt", true);
            assertThat(apply("non", "STRING_TO_BOOL", "src", "tgt")).containsEntry("tgt", false);
        }

        @Test
        @DisplayName("STRING_TO_BOOL throws (swallowed) for an unrecognized token")
        void stringToBoolInvalid() {
            Map<String, Object> input = new HashMap<>(Map.of("src", "maybe"));
            Map<String, Object> output = new HashMap<>(input);
            MappingRuleData rule = new MappingRuleData(1L, "src", "tgt", MappingType.FORMAT_CHANGE, "STRING_TO_BOOL");

            assertThatCode(() -> strategy.apply(rule, input, output)).doesNotThrowAnyException();
            assertThat(output).doesNotContainKey("tgt");
        }

        @Test
        @DisplayName("NUMBER_TO_STRING / BOOL_TO_STRING / blank expression stringify the value")
        void toStringConversions() {
            assertThat(apply(42, "NUMBER_TO_STRING", "src", "tgt")).containsEntry("tgt", "42");
            assertThat(apply(true, "BOOL_TO_STRING", "src", "tgt")).containsEntry("tgt", "true");
            assertThat(apply(42, "", "src", "tgt")).containsEntry("tgt", "42");
        }
    }

    @Nested
    @DisplayName("date/epoch conversions")
    class DateConversions {

        @Test
        @DisplayName("DATE_TO_UNIX converts an ISO date string to epoch seconds")
        void dateToUnix() {
            Map<String, Object> out = apply("1970-01-01", "DATE_TO_UNIX", "src", "tgt");
            assertThat(out).containsEntry("tgt", 0L);
        }

        @Test
        @DisplayName("DATE_TO_UNIX passes through a numeric epoch as-is")
        void dateToUnixFromNumber() {
            Map<String, Object> out = apply(123456L, "DATE_TO_UNIX", "src", "tgt");
            assertThat(out).containsEntry("tgt", 123456L);
        }

        @Test
        @DisplayName("UNIX_TO_DATE converts epoch seconds to an ISO date string")
        void unixToDate() {
            Map<String, Object> out = apply(0L, "UNIX_TO_DATE", "src", "tgt");
            assertThat(out).containsEntry("tgt", "1970-01-01");
        }

        @Test
        @DisplayName("custom pattern conversion using 'srcFormat|targetFormat'")
        void customPattern() {
            Map<String, Object> out = apply("25/12/2024", "dd/MM/yyyy|yyyy-MM-dd", "src", "tgt");
            assertThat(out).containsEntry("tgt", "2024-12-25");
        }

        @Test
        @DisplayName("throws (swallowed) for an unknown expression without a pipe separator")
        void unknownExpressionSwallowed() {
            Map<String, Object> input = new HashMap<>(Map.of("src", "2024-12-25"));
            Map<String, Object> output = new HashMap<>(input);
            MappingRuleData rule = new MappingRuleData(1L, "src", "tgt", MappingType.FORMAT_CHANGE, "NOT_A_REAL_CONVERSION");

            assertThatCode(() -> strategy.apply(rule, input, output)).doesNotThrowAnyException();
            assertThat(output).doesNotContainKey("tgt");
        }
    }

    @Test
    @DisplayName("skips silently when the sourceField is missing")
    void missingSourceField() {
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> output = new HashMap<>();
        MappingRuleData rule = new MappingRuleData(1L, "missing", "tgt", MappingType.FORMAT_CHANGE, "STRING_TO_INT");

        strategy.apply(rule, input, output);

        assertThat(output).doesNotContainKey("tgt");
    }
}
