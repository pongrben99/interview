package com.example.alerting.event;

import com.example.alerting.shared.FieldError;
import com.example.alerting.shared.ValidationException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Completes a validated event: decides its severity, adds derived attributes and settles its external id
 * (docs/02, section 6.5, step 4).
 *
 * <ul>
 *   <li><strong>Severity.</strong> A severity the sender supplied always wins. Otherwise the category's
 *       {@link SeverityNormalizer} derives it from the attributes. If that is impossible (no normalizer for
 *       the category, or the needed attribute is missing) the sender gets a {@code severity} field error that
 *       says what to supply.</li>
 *   <li><strong>Derived attributes.</strong> Whatever the normalizer computed, for example the size of a
 *       market move, is added when the sender did not supply that key; the sender's value is never
 *       overwritten. This happens even when the sender supplied the severity.</li>
 *   <li><strong>External id.</strong> The sender's if present, otherwise {@link ExternalIds#derive}.</li>
 * </ul>
 *
 * <p>The class is pure; it is wired as a bean in {@link EventConfiguration}.
 */
public final class EventNormalizer {

    private final Map<String, SeverityNormalizer> normalizersByCategory = new HashMap<>();

    /**
     * Creates the normalizer.
     *
     * @param severityNormalizers one normalizer per category that can derive severity
     * @throws IllegalArgumentException if two normalizers serve the same category
     */
    public EventNormalizer(Collection<? extends SeverityNormalizer> severityNormalizers) {
        for (SeverityNormalizer normalizer : severityNormalizers) {
            if (normalizersByCategory.putIfAbsent(normalizer.category(), normalizer) != null) {
                throw new IllegalArgumentException(
                        "Two severity normalizers are registered for category " + normalizer.category());
            }
        }
    }

    /**
     * Completes a validated event.
     *
     * @param event the event as returned by {@link EventValidator#validate}
     * @return the external id, severity and attributes to store
     * @throws ValidationException if the severity was not supplied and cannot be derived
     */
    public NormalizedEvent normalize(NewEvent event) {
        Optional<SeverityNormalizer> normalizer = Optional.ofNullable(normalizersByCategory.get(event.category()));
        Optional<Derivation> derivation = normalizer.flatMap(n -> n.derive(event.attributes()));

        int severity;
        if (event.severity() != null) {
            severity = event.severity();
        } else if (derivation.isPresent()) {
            severity = derivation.get().severity();
        } else {
            throw new ValidationException(List.of(new FieldError("severity", missingSeverityMessage(event, normalizer))));
        }

        Map<String, Object> attributes = new LinkedHashMap<>(event.attributes());
        derivation.ifPresent(d -> d.derivedAttributes().forEach(attributes::putIfAbsent));

        String externalId = event.externalId() != null
                ? event.externalId()
                : ExternalIds.derive(event.category(), event.title(), event.occurredAt(), event.region());
        return new NormalizedEvent(externalId, severity, attributes);
    }

    private static String missingSeverityMessage(NewEvent event, Optional<SeverityNormalizer> normalizer) {
        return normalizer
                .map(n -> "is required: it cannot be derived for category " + event.category() + " without "
                        + n.requirement())
                .orElse("is required for category " + event.category() + ", which has no automatic severity");
    }
}
