package com.example.alerting.event;

import com.example.alerting.shared.Guards;
import java.util.Map;

/**
 * What a {@link SeverityNormalizer} worked out from an event's attributes.
 *
 * @param severity the severity from 1 to 5
 * @param derivedAttributes extra attributes the normalizer computed, for example the size of a market
 *        move; they are added to the event only where the sender did not supply the key itself
 */
public record Derivation(int severity, Map<String, Object> derivedAttributes) {

    /**
     * Creates a derivation.
     *
     * @throws IllegalArgumentException if the severity is outside the 1 to 5 scale
     */
    public Derivation {
        Guards.requireInRange(severity, Severity.MIN_LEVEL, Severity.MAX_LEVEL, "severity");
        derivedAttributes = Map.copyOf(derivedAttributes);
    }
}
