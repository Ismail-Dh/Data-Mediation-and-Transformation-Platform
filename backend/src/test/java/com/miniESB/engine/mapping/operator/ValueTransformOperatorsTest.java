package com.miniESB.engine.mapping.operator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Value-transform operators — unit tests")
class ValueTransformOperatorsTest {

    @Nested
    @DisplayName("LowercaseOperator")
    class Lowercase {
        private final LowercaseOperator op = new LowercaseOperator();

        @Test
        @DisplayName("supports() matches 'LOWERCASE' case-insensitively")
        void supports() {
            assertThat(op.supports("LOWERCASE")).isTrue();
            assertThat(op.supports("lowercase")).isTrue();
            assertThat(op.supports("UPPERCASE")).isFalse();
        }

        @Test
        @DisplayName("apply() lowercases the value")
        void apply() {
            assertThat(op.apply("HeLLo", "LOWERCASE")).isEqualTo("hello");
        }
    }

    @Nested
    @DisplayName("UppercaseOperator")
    class Uppercase {
        private final UppercaseOperator op = new UppercaseOperator();

        @Test
        @DisplayName("supports() matches 'UPPERCASE' case-insensitively")
        void supports() {
            assertThat(op.supports("UPPERCASE")).isTrue();
            assertThat(op.supports("uppercase")).isTrue();
            assertThat(op.supports("TRIM")).isFalse();
        }

        @Test
        @DisplayName("apply() uppercases the value")
        void apply() {
            assertThat(op.apply("HeLLo", "UPPERCASE")).isEqualTo("HELLO");
        }
    }

    @Nested
    @DisplayName("TrimOperator")
    class Trim {
        private final TrimOperator op = new TrimOperator();

        @Test
        @DisplayName("supports() matches 'TRIM' case-insensitively")
        void supports() {
            assertThat(op.supports("TRIM")).isTrue();
            assertThat(op.supports("trim")).isTrue();
            assertThat(op.supports("SPLIT:,:0")).isFalse();
        }

        @Test
        @DisplayName("apply() trims leading/trailing whitespace")
        void apply() {
            assertThat(op.apply("  hello  ", "TRIM")).isEqualTo("hello");
        }
    }

    @Nested
    @DisplayName("SplitOperator")
    class Split {
        private final SplitOperator op = new SplitOperator();

        @Test
        @DisplayName("supports() matches expressions starting with 'SPLIT:'")
        void supports() {
            assertThat(op.supports("SPLIT:,:0")).isTrue();
            assertThat(op.supports("split:,:0")).isTrue();
            assertThat(op.supports("TRIM")).isFalse();
        }

        @Test
        @DisplayName("apply() splits by separator and returns the trimmed token at index")
        void apply() {
            assertThat(op.apply("a, b, c", "SPLIT:,:1")).isEqualTo("b");
        }

        @Test
        @DisplayName("apply() throws for a malformed expression (missing index)")
        void malformedExpression() {
            assertThatThrownBy(() -> op.apply("a,b", "SPLIT:,"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SPLIT format");
        }

        @Test
        @DisplayName("apply() throws when the index is out of bounds")
        void indexOutOfBounds() {
            assertThatThrownBy(() -> op.apply("a,b", "SPLIT:,:5"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("out of bounds");
        }

        @Test
        @DisplayName("apply() throws for a negative index")
        void negativeIndex() {
            assertThatThrownBy(() -> op.apply("a,b", "SPLIT:,:-1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("RegexReplaceOperator")
    class RegexReplace {
        private final RegexReplaceOperator op = new RegexReplaceOperator();

        @Test
        @DisplayName("supports() matches expressions starting with 'REGEX_REPLACE:'")
        void supports() {
            assertThat(op.supports("REGEX_REPLACE:[0-9]:X")).isTrue();
            assertThat(op.supports("regex_replace:[0-9]:X")).isTrue();
            assertThat(op.supports("SPLIT:,:0")).isFalse();
        }

        @Test
        @DisplayName("apply() replaces all regex matches with the replacement")
        void apply() {
            assertThat(op.apply("abc123def456", "REGEX_REPLACE:[0-9]+:#")).isEqualTo("abc#def#");
        }

        @Test
        @DisplayName("apply() supports an empty replacement")
        void emptyReplacement() {
            assertThat(op.apply("abc123", "REGEX_REPLACE:[0-9]+:")).isEqualTo("abc");
        }

        @Test
        @DisplayName("apply() throws when the replacement separator is missing")
        void malformedExpression() {
            assertThatThrownBy(() -> op.apply("abc", "REGEX_REPLACE:pattern-only"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("REGEX_REPLACE format");
        }
    }
}
