package com.example.alerting.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link Severity}: the 1 to 5 scale and its names. */
class SeverityTest {

    @ParameterizedTest(name = "level {0} is {1}")
    @CsvSource({"1, INFO", "2, LOW", "3, MEDIUM", "4, HIGH", "5, CRITICAL"})
    void everyLevelMapsToItsNameAndBack(int level, Severity expected) {
        assertThat(Severity.fromLevel(level)).isEqualTo(expected);
        assertThat(expected.level()).isEqualTo(level);
    }

    @Test
    void theScaleRunsFromOneToFive() {
        assertThat(Severity.MIN_LEVEL).isEqualTo(Severity.INFO.level());
        assertThat(Severity.MAX_LEVEL).isEqualTo(Severity.CRITICAL.level());
        assertThat(Severity.values()).hasSize(Severity.MAX_LEVEL - Severity.MIN_LEVEL + 1);
    }

    @ParameterizedTest(name = "level {0} is invalid")
    @ValueSource(ints = {0, 6, -1, 100})
    void fromLevelRejectsLevelsOutsideTheScale(int level) {
        assertThatThrownBy(() -> Severity.fromLevel(level))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("severity must be between 1 and 5 but was " + level);
    }
}
