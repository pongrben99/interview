package com.example.alerting.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ThresholdScaleTest {

    private final ThresholdScale scale = new ThresholdScale(List.of(4.0, 5.0, 6.0, 7.0), "test.thresholds");

    @ParameterizedTest
    @CsvSource({"-2.0,1", "0.0,1", "3.999,1", "4.0,2", "4.9,2", "5.0,3", "5.999,3", "6.0,4", "6.9,4", "7.0,5", "9.5,5"})
    void aValueBelongsToTheHighestLevelWhoseThresholdItReaches(double value, int expectedLevel) {
        assertThat(scale.levelFor(value)).isEqualTo(expectedLevel);
    }

    @Test
    void theLevelsCoverTheWholeSeverityScale() {
        assertThat(scale.levelFor(Double.NEGATIVE_INFINITY)).isEqualTo(Severity.MIN_LEVEL);
        assertThat(scale.levelFor(Double.POSITIVE_INFINITY)).isEqualTo(Severity.MAX_LEVEL);
    }

    @Test
    void requiresExactlyFourThresholds() {
        assertThatThrownBy(() -> new ThresholdScale(List.of(1.0, 2.0, 3.0), "my.key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("my.key needs exactly 4 thresholds but has 3");
        assertThatThrownBy(() -> new ThresholdScale(List.of(1.0, 2.0, 3.0, 4.0, 5.0), "my.key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs exactly 4 thresholds but has 5");
        assertThatThrownBy(() -> new ThresholdScale(null, "my.key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("my.key needs exactly 4 thresholds but has 0");
    }

    @Test
    void requiresStrictlyAscendingThresholds() {
        assertThatThrownBy(() -> new ThresholdScale(List.of(1.0, 2.0, 2.0, 3.0), "my.key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("my.key must be strictly ascending");
        assertThatThrownBy(() -> new ThresholdScale(List.of(4.0, 3.0, 2.0, 1.0), "my.key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly ascending");
    }

    @Test
    void requiresFiniteNumbers() {
        assertThatThrownBy(() -> new ThresholdScale(List.of(1.0, 2.0, Double.NaN, 4.0), "my.key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("my.key must contain only finite numbers");
        assertThatThrownBy(() -> new ThresholdScale(List.of(1.0, 2.0, 3.0, Double.POSITIVE_INFINITY), "my.key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("my.key must contain only finite numbers");
        assertThatThrownBy(() -> new ThresholdScale(Arrays.asList(1.0, null, 3.0, 4.0), "my.key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("my.key must contain only finite numbers");
    }
}
