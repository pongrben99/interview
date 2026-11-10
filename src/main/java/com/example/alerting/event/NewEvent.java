package com.example.alerting.event;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An event as a source submits it, before validation and normalization.
 *
 * <p>This is the one input of {@link EventIngestionService}: the push API, the simulator and any future
 * connector all produce a {@code NewEvent}, so every path gets the same checks. Fields are exactly those
 * of docs/02 section 6.1. Optional fields may be {@code null}; nothing here is trusted until
 * {@link EventValidator} has seen it.
 *
 * @param source the source code, for example {@code manual}; {@code null} means {@code manual}
 * @param externalId the source's own id for the event; {@code null} lets the service derive one from the
 *        content, so even naive clients are idempotent
 * @param category the category code, for example {@code DISASTER}
 * @param severity importance from 1 to 5, or {@code null} to derive it from the attributes
 * @param title the headline
 * @param summary an optional longer text
 * @param url an optional link, http or https
 * @param region an optional region code such as {@code JP} or {@code US-CA}
 * @param occurredAt when the event happened
 * @param attributes category-specific facts: scalar values (text, number, boolean) under short keys
 */
public record NewEvent(String source, String externalId, String category, Integer severity, String title,
                       String summary, String url, String region, Instant occurredAt,
                       Map<String, Object> attributes) {

    /**
     * Creates the command. The attributes are copied so the command cannot change afterwards;
     * {@code null} attributes become an empty map. Null <em>values</em> are kept on purpose: the validator
     * reports them, which a copying factory such as {@code Map.copyOf} would turn into a crash.
     */
    public NewEvent {
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
