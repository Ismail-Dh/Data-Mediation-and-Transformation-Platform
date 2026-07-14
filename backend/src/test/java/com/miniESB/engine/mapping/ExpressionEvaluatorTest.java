package com.miniESB.engine.mapping;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ExpressionEvaluator — unit tests")
class ExpressionEvaluatorTest {

    @Nested
    @DisplayName("isAggregation / isConditional")
    class Predicates {

        @Test
        @DisplayName("recognizes all supported aggregation prefixes, case-insensitively")
        void aggregationPrefixes() {
            assertThat(ExpressionEvaluator.isAggregation("SUM:items[].price")).isTrue();
            assertThat(ExpressionEvaluator.isAggregation("avg:items[].price")).isTrue();
            assertThat(ExpressionEvaluator.isAggregation("Count:items[]")).isTrue();
            assertThat(ExpressionEvaluator.isAggregation("MIN:items[].price")).isTrue();
            assertThat(ExpressionEvaluator.isAggregation("MAX:items[].price")).isTrue();
        }

        @Test
        @DisplayName("returns false for non-aggregation expressions")
        void notAggregation() {
            assertThat(ExpressionEvaluator.isAggregation("{a} + {b}")).isFalse();
        }

        @Test
        @DisplayName("recognizes IF: prefix case-insensitively")
        void conditionalPrefix() {
            assertThat(ExpressionEvaluator.isConditional("IF:age:gt:18:adult:minor")).isTrue();
            assertThat(ExpressionEvaluator.isConditional("if:age:gt:18:adult:minor")).isTrue();
            assertThat(ExpressionEvaluator.isConditional("{a}+{b}")).isFalse();
        }
    }

    @Nested
    @DisplayName("evaluateArithmetic")
    class Arithmetic {

        @Test
        @DisplayName("substitutes fields and evaluates the arithmetic expression")
        void basicArithmetic() {
            Map<String, Object> input = Map.of("price", 100, "tva", 0.2);
            Object result = ExpressionEvaluator.evaluateArithmetic("{price} * (1+{tva})", input);
            assertThat((Double) result).isEqualTo(120.0, within(0.0001));
        }

        @Test
        @DisplayName("supports nested field paths")
        void nestedField() {
            Map<String, Object> input = Map.of("order", Map.of("price", 50));
            Object result = ExpressionEvaluator.evaluateArithmetic("{order.price} + 10", input);
            assertThat((Double) result).isEqualTo(60.0, within(0.0001));
        }

        @Test
        @DisplayName("throws when a referenced field is missing")
        void missingField() {
            Map<String, Object> input = Map.of("price", 100);
            assertThatThrownBy(() -> ExpressionEvaluator.evaluateArithmetic("{price} + {tva}", input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tva");
        }

        @Test
        @DisplayName("throws when a referenced field is not a number")
        void nonNumericField() {
            Map<String, Object> input = Map.of("price", "abc");
            assertThatThrownBy(() -> ExpressionEvaluator.evaluateArithmetic("{price} + 1", input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not a number");
        }

        @Test
        @DisplayName("throws on invalid arithmetic syntax")
        void invalidSyntax() {
            Map<String, Object> input = Map.of("price", 1);
            assertThatThrownBy(() -> ExpressionEvaluator.evaluateArithmetic("{price} + + ", input))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("evaluateConditional")
    class Conditional {

        @Test
        @DisplayName("eq operator matches case-insensitively")
        void eqOperator() {
            Map<String, Object> input = Map.of("status", "Active");
            Object result = ExpressionEvaluator.evaluateConditional("IF:status:eq:active:yes:no", input);
            assertThat(result).isEqualTo("yes");
        }

        @Test
        @DisplayName("ne operator returns ifFalse branch when equal")
        void neOperator() {
            Map<String, Object> input = Map.of("status", "active");
            Object result = ExpressionEvaluator.evaluateConditional("IF:status:ne:active:yes:no", input);
            assertThat(result).isEqualTo("no");
        }

        @Test
        @DisplayName("contains operator is case-insensitive substring match")
        void containsOperator() {
            Map<String, Object> input = Map.of("email", "alice@example.com");
            Object result = ExpressionEvaluator.evaluateConditional("IF:email:contains:EXAMPLE:yes:no", input);
            assertThat(result).isEqualTo("yes");
        }

        @Test
        @DisplayName("numeric comparison operators gt/lt/gte/lte")
        void numericOperators() {
            Map<String, Object> input = Map.of("age", 20);
            assertThat(ExpressionEvaluator.evaluateConditional("IF:age:gt:18:adult:minor", input)).isEqualTo("adult");
            assertThat(ExpressionEvaluator.evaluateConditional("IF:age:lt:18:adult:minor", input)).isEqualTo("minor");
            assertThat(ExpressionEvaluator.evaluateConditional("IF:age:gte:20:ok:ko", input)).isEqualTo("ok");
            assertThat(ExpressionEvaluator.evaluateConditional("IF:age:lte:20:ok:ko", input)).isEqualTo("ok");
        }

        @Test
        @DisplayName("throws for a malformed IF expression (too few parts)")
        void malformedExpression() {
            Map<String, Object> input = Map.of("age", 20);
            assertThatThrownBy(() -> ExpressionEvaluator.evaluateConditional("IF:age:gt:18", input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("IF format");
        }

        @Test
        @DisplayName("throws when the field is missing")
        void missingField() {
            Map<String, Object> input = Map.of("other", 1);
            assertThatThrownBy(() -> ExpressionEvaluator.evaluateConditional("IF:age:gt:18:adult:minor", input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("age");
        }

        @Test
        @DisplayName("throws for an unknown operator")
        void unknownOperator() {
            Map<String, Object> input = Map.of("age", 20);
            assertThatThrownBy(() -> ExpressionEvaluator.evaluateConditional("IF:age:foo:18:a:b", input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown IF operator");
        }
    }

    @Nested
    @DisplayName("evaluateAggregation")
    class Aggregation {

        @Test
        @DisplayName("SUM over a numeric sub-field")
        void sum() {
            Map<String, Object> input = Map.of("items", List.of(
                    Map.of("price", 10), Map.of("price", 20), Map.of("price", 30)));
            Object result = ExpressionEvaluator.evaluateAggregation("SUM:items[].price", input);
            assertThat((Double) result).isEqualTo(60.0, within(0.0001));
        }

        @Test
        @DisplayName("AVG over a numeric sub-field")
        void avg() {
            Map<String, Object> input = Map.of("items", List.of(
                    Map.of("price", 10), Map.of("price", 20)));
            Object result = ExpressionEvaluator.evaluateAggregation("AVG:items[].price", input);
            assertThat((Double) result).isEqualTo(15.0, within(0.0001));
        }

        @Test
        @DisplayName("MIN and MAX over a numeric sub-field")
        void minMax() {
            Map<String, Object> input = Map.of("items", List.of(
                    Map.of("price", 10), Map.of("price", 30), Map.of("price", 5)));
            assertThat((Double) ExpressionEvaluator.evaluateAggregation("MIN:items[].price", input))
                    .isEqualTo(5.0, within(0.0001));
            assertThat((Double) ExpressionEvaluator.evaluateAggregation("MAX:items[].price", input))
                    .isEqualTo(30.0, within(0.0001));
        }

        @Test
        @DisplayName("COUNT returns the array size, sub-field not required")
        void count() {
            Map<String, Object> input = Map.of("items", List.of(Map.of("a", 1), Map.of("a", 2)));
            Object result = ExpressionEvaluator.evaluateAggregation("COUNT:items[]", input);
            assertThat(result).isEqualTo(2);
        }

        @Test
        @DisplayName("throws when path is not an array")
        void notAnArray() {
            Map<String, Object> input = Map.of("items", "not-a-list");
            assertThatThrownBy(() -> ExpressionEvaluator.evaluateAggregation("SUM:items[].price", input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not an array");
        }

        @Test
        @DisplayName("throws when the path has neither '[].' nor trailing '[]'")
        void invalidPath() {
            Map<String, Object> input = Map.of("items", List.of());
            assertThatThrownBy(() -> ExpressionEvaluator.evaluateAggregation("SUM:items.price", input))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws for SUM/AVG/MIN/MAX without a sub-field")
        void missingSubField() {
            Map<String, Object> input = Map.of("items", List.of(1, 2, 3));
            assertThatThrownBy(() -> ExpressionEvaluator.evaluateAggregation("SUM:items[]", input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requires a sub-field");
        }

        @Test
        @DisplayName("throws when the array is empty")
        void emptyArray() {
            Map<String, Object> input = Map.of("items", List.of());
            assertThatThrownBy(() -> ExpressionEvaluator.evaluateAggregation("SUM:items[].price", input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("throws when the sub-field is missing on an item")
        void missingSubFieldValue() {
            Map<String, Object> input = Map.of("items", List.of(Map.of("other", 1)));
            assertThatThrownBy(() -> ExpressionEvaluator.evaluateAggregation("SUM:items[].price", input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("throws when the sub-field value is not numeric")
        void nonNumericSubFieldValue() {
            Map<String, Object> input = Map.of("items", List.of(Map.of("price", "abc")));
            assertThatThrownBy(() -> ExpressionEvaluator.evaluateAggregation("SUM:items[].price", input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not a number");
        }
    }
}
