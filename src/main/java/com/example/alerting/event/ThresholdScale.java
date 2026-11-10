package com.example.alerting.event;

import java.util.List;

/**
 * Maps a measurement onto the severity scale with four ascending thresholds.
 *
 * <p>With thresholds {@code [4, 5, 6, 7]} a value below 4 is level 1, 4 up to (not including) 5 is level 2,
 * and so on; 7 and above is level 5. A value exactly on a threshold belongs to the higher level.
 */
public final class ThresholdScale {

    private static final int THRESHOLD_COUNT = Severity.MAX_LEVEL - Severity.MIN_LEVEL;

    private final double[] thresholds;

    /**
     * Creates a scale.
     *
     * @param thresholds the lower bounds of levels 2 to 5: exactly four finite numbers, strictly ascending
     * @param name the configuration key, used in error messages
     * @throws IllegalArgumentException if the thresholds are not four finite, strictly ascending numbers
     */
    public ThresholdScale(List<Double> thresholds, String name) {
        if (thresholds == null || thresholds.size() != THRESHOLD_COUNT) {
            throw new IllegalArgumentException(
                    name + " needs exactly " + THRESHOLD_COUNT + " thresholds but has "
                            + (thresholds == null ? 0 : thresholds.size()));
        }
        this.thresholds = new double[THRESHOLD_COUNT];
        for (int i = 0; i < THRESHOLD_COUNT; i++) {
            Double value = thresholds.get(i);
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " must contain only finite numbers");
            }
            if (i > 0 && value <= this.thresholds[i - 1]) {
                throw new IllegalArgumentException(name + " must be strictly ascending but was " + thresholds);
            }
            this.thresholds[i] = value;
        }
    }

    /**
     * Finds the severity level of a measurement.
     *
     * @param value the measurement
     * @return a level from {@link Severity#MIN_LEVEL} to {@link Severity#MAX_LEVEL}
     */
    public int levelFor(double value) {
        int level = Severity.MIN_LEVEL;
        for (double threshold : thresholds) {
            if (value >= threshold) {
                level++;
            }
        }
        return level;
    }
}
