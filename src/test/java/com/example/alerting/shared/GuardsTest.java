package com.example.alerting.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link Guards}: valid values pass unchanged, invalid ones are rejected with a message naming the field. */
class GuardsTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n \t"})
    void requireNonBlankRejectsNullEmptyAndBlankText(String value) {
        assertThatThrownBy(() -> Guards.requireNonBlank(value, "title"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title must not be blank");
    }

    @Test
    void requireNonBlankReturnsTheTextWithoutSurroundingWhitespace() {
        assertThat(Guards.requireNonBlank("  Hello world \n", "title")).isEqualTo("Hello world");
    }

    @Test
    void requireNonBlankKeepsInnerWhitespace() {
        assertThat(Guards.requireNonBlank("a  b", "title")).isEqualTo("a  b");
    }

    @ParameterizedTest(name = "{0} is inside [{1}, {2}]")
    @CsvSource({"1, 1, 5", "3, 1, 5", "5, 1, 5", "0, 0, 0", "-2, -5, -1"})
    void requireInRangeAcceptsValuesInsideTheClosedRange(int value, int min, int max) {
        assertThat(Guards.requireInRange(value, min, max, "severity")).isEqualTo(value);
    }

    @ParameterizedTest(name = "{0} is outside [{1}, {2}]")
    @CsvSource({"0, 1, 5", "6, 1, 5", "-1, 0, 10", "11, 0, 10"})
    void requireInRangeRejectsValuesOutsideTheRange(int value, int min, int max) {
        assertThatThrownBy(() -> Guards.requireInRange(value, min, max, "severity"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("severity must be between " + min + " and " + max + " but was " + value);
    }

    @ParameterizedTest(name = "{0} is at least {1}")
    @CsvSource({"1, 1", "2, 1", "0, 0", "-3, -3"})
    void requireAtLeastAcceptsValuesAtOrAboveTheBound(int value, int min) {
        assertThat(Guards.requireAtLeast(value, min, "attemptNo")).isEqualTo(value);
    }

    @ParameterizedTest(name = "{0} is below {1}")
    @CsvSource({"0, 1", "-1, 0", "-10, -9"})
    void requireAtLeastRejectsValuesBelowTheBound(int value, int min) {
        assertThatThrownBy(() -> Guards.requireAtLeast(value, min, "attemptNo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("attemptNo must be at least " + min + " but was " + value);
    }
}
