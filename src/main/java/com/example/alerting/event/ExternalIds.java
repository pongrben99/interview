package com.example.alerting.event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Derives an external id for events whose sender did not supply one, so that naive clients are idempotent
 * too (docs/02, section 6.5).
 *
 * <p>The id is {@code h:} followed by the SHA-256 of the category, title, occurrence time and region. The
 * same content always yields the same id; any difference yields a different one. The fields are joined with
 * the ASCII unit separator, which cannot appear in a validated title, so two different events can never
 * produce the same input text.
 */
public final class ExternalIds {

    /** Marks a derived id, so it can never be mistaken for one a source chose. */
    static final String PREFIX = "h:";

    private static final char SEPARATOR = '\u001F';

    private ExternalIds() {
    }

    /**
     * Derives the id from the identifying content of an event.
     *
     * @param category the category code
     * @param title the cleaned title
     * @param occurredAt when the event happened
     * @param region the region code, or {@code null}; case does not matter
     * @return {@code h:} and 64 lower-case hexadecimal digits
     */
    public static String derive(String category, String title, Instant occurredAt, String region) {
        String material = category + SEPARATOR + title + SEPARATOR + occurredAt + SEPARATOR
                + (region == null ? "" : region.toUpperCase(Locale.ROOT));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return PREFIX + HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every Java platform; reaching this means a broken runtime.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
