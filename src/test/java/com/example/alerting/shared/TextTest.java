package com.example.alerting.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link Text}: truncation fits the column and never breaks a character; blank text becomes null. */
class TextTest {

    private static final String ELLIPSIS = Text.ELLIPSIS;

    @Test
    void truncateKeepsNull() {
        assertThat(Text.truncate(null, 10)).isNull();
    }

    @ParameterizedTest(name = "\"{0}\" fits into {1} characters")
    @CsvSource({"'', 5", "abc, 5", "abcde, 5", "a, 1"})
    void truncateReturnsTextThatAlreadyFitsUnchanged(String value, int max) {
        assertThat(Text.truncate(value, max)).isSameAs(value);
    }

    @Test
    void truncateCutsLongTextAndMarksTheCutWithAnEllipsis() {
        String result = Text.truncate("abcdefghij", 5);

        assertThat(result).isEqualTo("abcd" + ELLIPSIS).hasSize(5);
    }

    @Test
    void truncateToOneCharacterLeavesOnlyTheEllipsis() {
        assertThat(Text.truncate("abc", 1)).isEqualTo(ELLIPSIS);
    }

    @Test
    void truncateDoesNotSplitASurrogatePairAtTheCut() {
        // "ab", one emoji written as two chars (high and low surrogate), then "cd": length 6.
        String value = "ab😀cd";

        String result = Text.truncate(value, 4);

        // Cutting after 3 chars would leave a lone high surrogate, so the cut moves one char earlier.
        assertThat(result).isEqualTo("ab" + ELLIPSIS);
        assertThat(result.length()).isLessThanOrEqualTo(4);
    }

    @Test
    void truncateKeepsAWholeSurrogatePairWhenTheCutFallsAfterIt() {
        // "a", emoji (2 chars), "bcd": length 6; cutting after 3 chars keeps the complete emoji.
        String value = "a😀bcd";

        String result = Text.truncate(value, 4);

        assertThat(result).isEqualTo("a😀" + ELLIPSIS).hasSize(4);
    }

    @ParameterizedTest(name = "maxLength {0} is invalid")
    @ValueSource(ints = {0, -1, -100})
    void truncateRejectsAMaximumBelowOne(int max) {
        assertThatThrownBy(() -> Text.truncate("abc", max))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxLength must be at least 1 but was " + max);
    }

    @Test
    void printableRendersNullAsTheWordNull() {
        assertThat(Text.printable(null, 10)).isEqualTo("null");
    }

    @Test
    void printableKeepsHarmlessTextUnchanged() {
        assertThat(Text.printable("title with spaces, 'quotes' and ünïcode 😀", 100))
                .isEqualTo("title with spaces, 'quotes' and ünïcode 😀");
    }

    @Test
    void printableReplacesEveryControlCharacterWithAQuestionMark() {
        assertThat(Text.printable("a\nb\r\nc\td\u0000e\u007ff\u009fg", 100)).isEqualTo("a?b??c?d?e?f?g");
    }

    @Test
    void printableShortensLongTextWithAnEllipsis() {
        assertThat(Text.printable("x".repeat(50), 10)).isEqualTo("x".repeat(9) + ELLIPSIS);
    }

    @Test
    void printableRejectsAMaximumBelowOne() {
        assertThatThrownBy(() -> Text.printable("abc", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "\"{0}\" is blank")
    @ValueSource(strings = {"", " ", "   ", "\t\n"})
    void blankToNullTurnsBlankTextIntoNull(String value) {
        assertThat(Text.blankToNull(value)).isNull();
    }

    @Test
    void blankToNullKeepsNull() {
        assertThat(Text.blankToNull(null)).isNull();
    }

    @Test
    void blankToNullStripsSurroundingWhitespace() {
        assertThat(Text.blankToNull("  value \n")).isEqualTo("value");
    }
}
