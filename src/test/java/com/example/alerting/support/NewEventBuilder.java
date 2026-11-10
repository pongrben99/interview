package com.example.alerting.support;

import com.example.alerting.event.NewEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds {@link NewEvent} commands for tests.
 *
 * <p>The defaults describe a valid earthquake at {@link TestEvents#NOW}: category DISASTER, a magnitude of
 * 6.4 and no severity, so each test states only what differs from "valid".
 */
public final class NewEventBuilder {

    private String source = "manual";
    private String externalId = "test-id-1";
    private String category = "DISASTER";
    private Integer severity;
    private String title = "M 6.4 earthquake near Hualien, Taiwan";
    private String summary;
    private String url;
    private String region = "TW";
    private Instant occurredAt = TestEvents.NOW.minusSeconds(60);
    private final Map<String, Object> attributes = new LinkedHashMap<>(Map.of("magnitude", 6.4));

    private NewEventBuilder() {
    }

    /**
     * Starts from a valid earthquake.
     *
     * @return a new builder
     */
    public static NewEventBuilder anEvent() {
        return new NewEventBuilder();
    }

    public NewEventBuilder source(String value) {
        this.source = value;
        return this;
    }

    public NewEventBuilder externalId(String value) {
        this.externalId = value;
        return this;
    }

    public NewEventBuilder category(String value) {
        this.category = value;
        return this;
    }

    public NewEventBuilder severity(Integer value) {
        this.severity = value;
        return this;
    }

    public NewEventBuilder title(String value) {
        this.title = value;
        return this;
    }

    public NewEventBuilder summary(String value) {
        this.summary = value;
        return this;
    }

    public NewEventBuilder url(String value) {
        this.url = value;
        return this;
    }

    public NewEventBuilder region(String value) {
        this.region = value;
        return this;
    }

    public NewEventBuilder occurredAt(Instant value) {
        this.occurredAt = value;
        return this;
    }

    /**
     * Replaces all attributes.
     *
     * @param values the attributes; keys and values are taken as given
     * @return this builder
     */
    public NewEventBuilder attributes(Map<String, Object> values) {
        this.attributes.clear();
        this.attributes.putAll(values);
        return this;
    }

    /**
     * Adds or replaces one attribute; {@code null} values are allowed so that validation can be tested.
     *
     * @param key the attribute name
     * @param value the attribute value
     * @return this builder
     */
    public NewEventBuilder attribute(String key, Object value) {
        this.attributes.put(key, value);
        return this;
    }

    /**
     * Removes all attributes.
     *
     * @return this builder
     */
    public NewEventBuilder noAttributes() {
        this.attributes.clear();
        return this;
    }

    /**
     * Builds the command.
     *
     * @return the event
     */
    public NewEvent build() {
        return new NewEvent(source, externalId, category, severity, title, summary, url, region, occurredAt, attributes);
    }
}
