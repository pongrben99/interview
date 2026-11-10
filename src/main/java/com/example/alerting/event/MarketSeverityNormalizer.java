package com.example.alerting.event;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Severity of a market movement from its {@code changePct} attribute: the signed percent move against the
 * previous close.
 *
 * <p>The size of the move decides the level. With the default thresholds: under 1 % is level 1, 1 % to under
 * 2 % is 2, 2 % to under 5 % is 3, 5 % to under 10 % is 4 and 10 % or more is 5. The direction does not
 * change the level: a crash is as important as a rally.
 *
 * <p>The normalizer also derives two attributes so rules can use them: {@code moveMagnitudePct}, the
 * absolute size of the move, and {@code direction}, which is {@code UP}, {@code DOWN}, or {@code FLAT} for
 * a move of exactly zero (docs/02 names only UP and DOWN; FLAT is the honest value for no move).
 */
public final class MarketSeverityNormalizer implements SeverityNormalizer {

    /** The category served by this normalizer. */
    public static final String CATEGORY = "MARKET";

    static final String CHANGE_PCT = "changePct";
    static final String MOVE_MAGNITUDE_PCT = "moveMagnitudePct";
    static final String DIRECTION = "direction";

    private final ThresholdScale moveScale;

    /**
     * Creates the normalizer.
     *
     * @param changePctThresholds the lower bounds, in absolute percent, of severity levels 2 to 5
     * @throws IllegalArgumentException if they are not four finite, strictly ascending numbers
     */
    public MarketSeverityNormalizer(List<Double> changePctThresholds) {
        this.moveScale = new ThresholdScale(changePctThresholds, "alerting.severity.market.change-pct-thresholds");
    }

    @Override
    public String category() {
        return CATEGORY;
    }

    @Override
    public String requirement() {
        return "the numeric attribute '" + CHANGE_PCT + "'";
    }

    @Override
    public Optional<Derivation> derive(Map<String, Object> attributes) {
        OptionalDouble change = Attributes.finiteNumber(attributes, CHANGE_PCT);
        if (change.isEmpty()) {
            return Optional.empty();
        }
        double signed = change.getAsDouble();
        double magnitude = Math.abs(signed);
        Map<String, Object> derived = new LinkedHashMap<>();
        derived.put(MOVE_MAGNITUDE_PCT, magnitude);
        derived.put(DIRECTION, signed > 0 ? "UP" : signed < 0 ? "DOWN" : "FLAT");
        return Optional.of(new Derivation(moveScale.levelFor(magnitude), derived));
    }
}
