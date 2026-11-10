package com.example.alerting.web;

import com.example.alerting.event.NewEvent;
import com.example.alerting.shared.ValidationException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * The JSON body of {@code POST /api/v1/events} (docs/02, section 6.1).
 *
 * <p>It mirrors {@link NewEvent} field by field. The one difference is {@code occurredAt}: it arrives as
 * text and is parsed here, so a malformed time is reported as a clear field error instead of being silently
 * accepted as a number of seconds or milliseconds. Unknown JSON fields are rejected by the JSON settings.
 *
 * @param source the source code; defaults to {@code manual}
 * @param externalId the sender's id for the event; derived from the content when absent
 * @param category the category code
 * @param severity importance from 1 to 5; derived from the attributes when absent
 * @param title the headline
 * @param summary an optional longer text
 * @param url an optional http or https link
 * @param region an optional region code such as {@code JP}
 * @param occurredAt when it happened, an ISO-8601 instant such as {@code 2026-11-10T08:12:00Z}
 * @param attributes category-specific scalar facts
 */
public record PublishEventRequest(String source, String externalId, String category, Integer severity,
                                  String title, String summary, String url, String region, String occurredAt,
                                  Map<String, Object> attributes) {

    /**
     * Converts the request into the command the ingestion service takes.
     *
     * @return the command
     * @throws ValidationException if {@code occurredAt} is present but not an ISO-8601 instant
     */
    public NewEvent toCommand() {
        return new NewEvent(source, externalId, category, severity, title, summary, url, region,
                parseInstant(occurredAt), attributes);
    }

    private static Instant parseInstant(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text.strip());
        } catch (DateTimeParseException e) {
            throw ValidationException.of("occurredAt", "must be an ISO-8601 instant such as 2026-11-10T08:12:00Z");
        }
    }
}
