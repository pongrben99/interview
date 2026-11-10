package com.example.alerting.event;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Severity of a natural disaster, today an earthquake, from its {@code magnitude} attribute.
 *
 * <p>With the default thresholds: below 4.0 is level 1, 4.0 to 4.9 is 2, 5.0 to 5.9 is 3, 6.0 to 6.9 is 4
 * and 7.0 and above is 5. A {@code tsunami} attribute that is {@code true} raises the level by one, up to
 * the maximum of 5.
 */
public final class DisasterSeverityNormalizer implements SeverityNormalizer {

    /** The category served by this normalizer. */
    public static final String CATEGORY = "DISASTER";

    static final String MAGNITUDE = "magnitude";
    static final String TSUNAMI = "tsunami";

    private final ThresholdScale magnitudeScale;

    /**
     * Creates the normalizer.
     *
     * @param magnitudeThresholds the lower magnitude bounds of severity levels 2 to 5
     * @throws IllegalArgumentException if they are not four finite, strictly ascending numbers
     */
    public DisasterSeverityNormalizer(List<Double> magnitudeThresholds) {
        this.magnitudeScale = new ThresholdScale(magnitudeThresholds, "alerting.severity.disaster.magnitude-thresholds");
    }

    @Override
    public String category() {
        return CATEGORY;
    }

    @Override
    public String requirement() {
        return "the numeric attribute '" + MAGNITUDE + "'";
    }

    @Override
    public Optional<Derivation> derive(Map<String, Object> attributes) {
        OptionalDouble magnitude = Attributes.finiteNumber(attributes, MAGNITUDE);
        if (magnitude.isEmpty()) {
            return Optional.empty();
        }
        int level = magnitudeScale.levelFor(magnitude.getAsDouble());
        if (Attributes.isTrue(attributes, TSUNAMI)) {
            level = Math.min(level + 1, Severity.MAX_LEVEL);
        }
        return Optional.of(new Derivation(level, Map.of()));
    }
}
