package com.example.alerting.support;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds valid events for tests, so each test only states what it cares about.
 *
 * <p>{@link #quake()} and friends return a mutable <em>field map</em> that mirrors the JSON body of the push
 * API; tests change or remove single fields and then turn the map into JSON. For commands to the service
 * layer use {@link NewEventBuilder}.
 */
public final class TestEvents {

    /** A fixed "now" for tests that inject a clock. */
    public static final Instant NOW = Instant.parse("2026-11-10T09:00:00Z");

    private TestEvents() {
    }

    /**
     * A valid earthquake with a magnitude, a unique external id and no severity (so it is derived).
     *
     * @return the field map
     */
    public static Map<String, Object> quake() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("magnitude", 6.4);
        attributes.put("depthKm", 10);
        attributes.put("tsunami", false);
        attributes.put("place", "Hualien, Taiwan");
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("source", "manual");
        event.put("externalId", "test:" + UUID.randomUUID());
        event.put("category", "DISASTER");
        event.put("title", "M 6.4 earthquake near Hualien, Taiwan");
        event.put("summary", "Shallow quake at 10 km depth.");
        event.put("url", "https://example.com/events/quake");
        event.put("region", "TW");
        event.put("occurredAt", Instant.now().minusSeconds(60).toString());
        event.put("attributes", attributes);
        return event;
    }

    /**
     * A valid market move of minus 6.3 percent, without a severity.
     *
     * @return the field map
     */
    public static Map<String, Object> marketMove() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("symbol", "NVDA");
        attributes.put("changePct", -6.3);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("externalId", "test:" + UUID.randomUUID());
        event.put("category", "MARKET");
        event.put("title", "NVIDIA falls 6.3%");
        event.put("occurredAt", Instant.now().minusSeconds(30).toString());
        event.put("attributes", attributes);
        return event;
    }

    /**
     * The attribute map of a field map built by this class.
     *
     * @param event a field map returned by {@link #quake()} or {@link #marketMove()}
     * @return its mutable {@code attributes} entry
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> attributesOf(Map<String, Object> event) {
        return (Map<String, Object>) event.get("attributes");
    }

}
