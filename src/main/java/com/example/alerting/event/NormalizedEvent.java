package com.example.alerting.event;

import java.util.Map;

/**
 * The facts {@link EventNormalizer} adds to a validated event.
 *
 * @param externalId the id the event is stored under: the sender's, or a derived one
 * @param severity the final severity from 1 to 5
 * @param attributes the sender's attributes plus any attributes the category's normalizer derived
 */
public record NormalizedEvent(String externalId, int severity, Map<String, Object> attributes) {
}
