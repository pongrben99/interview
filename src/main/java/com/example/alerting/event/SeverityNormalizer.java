package com.example.alerting.event;

import java.util.Map;
import java.util.Optional;

/**
 * Turns a category's own measurements into the common 1 to 5 severity scale (docs/02, section 6.2).
 *
 * <p>An earthquake has a magnitude, a market move has a percentage, news has flags; a user rule can only
 * say "severity at least HIGH" if every category speaks the same scale. One implementation per category
 * does the translation. A category without a normalizer simply requires the sender to supply a severity,
 * so adding a category needs no code.
 *
 * <p>Implementations are pure: no I/O, no clock, no framework. Their thresholds come from configuration.
 */
public interface SeverityNormalizer {

    /**
     * The category this normalizer serves.
     *
     * @return the category code, for example {@code DISASTER}
     */
    String category();

    /**
     * Describes what the sender must provide for the severity to be derived. It completes the error message
     * when the severity is missing and cannot be derived.
     *
     * @return a short phrase such as {@code the numeric attribute 'magnitude'}
     */
    String requirement();

    /**
     * Derives the severity from an event's attributes.
     *
     * @param attributes the event's attributes
     * @return the derivation, or empty if the attributes do not contain what is needed
     */
    Optional<Derivation> derive(Map<String, Object> attributes);
}
