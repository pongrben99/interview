package com.example.alerting.event;

import com.example.alerting.shared.FieldError;
import com.example.alerting.shared.Text;
import com.example.alerting.shared.ValidationException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Checks and cleans a submitted event: the field rules of docs/02, section 6.1.
 *
 * <p>Every problem is collected and reported together, so a sender can fix a whole payload in one round
 * trip. On success the result holds the cleaned values: text trimmed, control characters removed from the
 * title and summary, identifiers stripped. The rules are:
 *
 * <ul>
 *   <li>{@code source} (optional) and {@code category}: letters, digits, {@code _ . -}; at most 50 and 30
 *       characters; case-sensitive, matched exactly against the stored codes later.</li>
 *   <li>{@code externalId} (optional): at most 200 characters, no control characters.</li>
 *   <li>{@code severity} (optional here, see {@link EventNormalizer}): 1 to 5.</li>
 *   <li>{@code title} (required): at most 300 characters after cleaning, not blank.</li>
 *   <li>{@code summary}: at most 2000 characters; line breaks and tabs are kept.</li>
 *   <li>{@code url}: at most 2000 characters, an absolute http or https URL.</li>
 *   <li>{@code region}: letters, digits, {@code _} and {@code -}; at most 50 characters.</li>
 *   <li>{@code occurredAt} (required): not more than {@link #MAX_CLOCK_SKEW} in the future.</li>
 *   <li>{@code attributes}: keys start with a letter and have at most 40 letters, digits or {@code _};
 *       values are text, numbers (finite) or booleans: no nulls, nesting or arrays. The serialized size
 *       limit is enforced later by {@link AttributesCodec}, once derived attributes are included.</li>
 * </ul>
 *
 * <p>Optional free text that is blank counts as absent, matching the {@link Event} entity. Identifiers
 * ({@code source}, {@code externalId}) must not be blank when present: a blank one is a client mistake that
 * would silently change meaning (default source, derived id).
 */
@Component
public class EventValidator {

    /** How far in the future an event may claim to have happened, to tolerate clock differences. */
    public static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    static final int MAX_SOURCE_LENGTH = 50;
    static final int MAX_EXTERNAL_ID_LENGTH = 200;
    static final int MAX_CATEGORY_LENGTH = 30;
    static final int MAX_TITLE_LENGTH = 300;
    static final int MAX_SUMMARY_LENGTH = 2000;
    static final int MAX_URL_LENGTH = 2000;
    static final int MAX_REGION_LENGTH = 50;

    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");
    private static final Pattern REGION = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern ATTRIBUTE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,39}");
    private static final int SHOWN_KEY_LENGTH = 40;

    /**
     * Validates and cleans an event.
     *
     * @param event the submitted event
     * @param now the current time, for the future-date rule
     * @return the event with cleaned values; optional fields that were blank are {@code null}
     * @throws ValidationException listing every rule the event breaks
     */
    public NewEvent validate(NewEvent event, Instant now) {
        List<FieldError> errors = new ArrayList<>();

        String source = event.source() == null ? null : code(event.source(), "source", MAX_SOURCE_LENGTH, errors);
        String externalId = event.externalId() == null ? null : externalId(event.externalId(), errors);
        String category = event.category() == null
                ? missing("category", errors)
                : code(event.category(), "category", MAX_CATEGORY_LENGTH, errors);
        Integer severity = severity(event.severity(), errors);
        String title = event.title() == null ? missing("title", errors) : title(event.title(), errors);
        String summary = freeText(event.summary(), "summary", MAX_SUMMARY_LENGTH, true, errors);
        String url = url(event.url(), errors);
        String region = region(event.region(), errors);
        if (event.occurredAt() == null) {
            missing("occurredAt", errors);
        } else if (event.occurredAt().isAfter(now.plus(MAX_CLOCK_SKEW))) {
            errors.add(new FieldError("occurredAt",
                    "must not be more than " + MAX_CLOCK_SKEW.toMinutes() + " minutes in the future"));
        }
        attributes(event.attributes(), errors);

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new NewEvent(source, externalId, category, severity, title, summary, url, region,
                event.occurredAt(), event.attributes());
    }

    private static String missing(String field, List<FieldError> errors) {
        errors.add(new FieldError(field, "is required"));
        return null;
    }

    private static String code(String value, String field, int maxLength, List<FieldError> errors) {
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            errors.add(new FieldError(field, "must not be blank"));
        } else if (stripped.length() > maxLength) {
            errors.add(new FieldError(field, "must be at most " + maxLength + " characters"));
        } else if (!CODE.matcher(stripped).matches()) {
            errors.add(new FieldError(field, "may only contain letters, digits, '_', '.' and '-'"));
        } else {
            return stripped;
        }
        return null;
    }

    private static String externalId(String value, List<FieldError> errors) {
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            errors.add(new FieldError("externalId", "must not be blank"));
        } else if (stripped.length() > MAX_EXTERNAL_ID_LENGTH) {
            errors.add(new FieldError("externalId", "must be at most " + MAX_EXTERNAL_ID_LENGTH + " characters"));
        } else if (stripped.codePoints().anyMatch(Character::isISOControl)) {
            errors.add(new FieldError("externalId", "must not contain control characters"));
        } else {
            return stripped;
        }
        return null;
    }

    private static Integer severity(Integer value, List<FieldError> errors) {
        if (value != null && (value < Severity.MIN_LEVEL || value > Severity.MAX_LEVEL)) {
            errors.add(new FieldError("severity",
                    "must be between " + Severity.MIN_LEVEL + " and " + Severity.MAX_LEVEL));
            return null;
        }
        return value;
    }

    private static String title(String value, List<FieldError> errors) {
        String cleaned = removeControlCharacters(value, false).strip();
        if (cleaned.isEmpty()) {
            errors.add(new FieldError("title", "must not be blank"));
        } else if (cleaned.length() > MAX_TITLE_LENGTH) {
            errors.add(new FieldError("title", "must be at most " + MAX_TITLE_LENGTH + " characters"));
        } else {
            return cleaned;
        }
        return null;
    }

    private static String freeText(String value, String field, int maxLength, boolean keepLineBreaks,
                                   List<FieldError> errors) {
        if (value == null) {
            return null;
        }
        String cleaned = removeControlCharacters(value, keepLineBreaks).strip();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > maxLength) {
            errors.add(new FieldError(field, "must be at most " + maxLength + " characters"));
            return null;
        }
        return cleaned;
    }

    private static String url(String value, List<FieldError> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.length() > MAX_URL_LENGTH) {
            errors.add(new FieldError("url", "must be at most " + MAX_URL_LENGTH + " characters"));
        } else if (!isHttpUrl(stripped)) {
            errors.add(new FieldError("url", "must be an absolute http or https URL"));
        } else {
            return stripped;
        }
        return null;
    }

    /**
     * Checks scheme and authority. The scheme is what matters for safety: it keeps {@code javascript:},
     * {@code data:} and {@code file:} links out. Java's {@link URI} reports no host for a Unicode domain name
     * or a name with an underscore, so an authority is accepted too; the URL is only ever shown, never fetched.
     */
    private static boolean isHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return (uri.getHost() != null || uri.getRawAuthority() != null)
                    && scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String region(String value, List<FieldError> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.length() > MAX_REGION_LENGTH) {
            errors.add(new FieldError("region", "must be at most " + MAX_REGION_LENGTH + " characters"));
        } else if (!REGION.matcher(stripped).matches()) {
            errors.add(new FieldError("region", "may only contain letters, digits, '_' and '-'"));
        } else {
            return stripped;
        }
        return null;
    }

    private static void attributes(Map<String, Object> attributes, List<FieldError> errors) {
        attributes.forEach((key, value) -> {
            if (key == null || !ATTRIBUTE_KEY.matcher(key).matches()) {
                errors.add(new FieldError("attributes", "the key '" + Text.printable(key, SHOWN_KEY_LENGTH)
                        + "' is invalid: a key starts "
                        + "with a letter and has at most 40 letters, digits or '_'"));
                return;
            }
            String field = "attributes." + key;
            if (value == null) {
                errors.add(new FieldError(field, "must not be null"));
            } else if (value instanceof Number number) {
                if (!Double.isFinite(number.doubleValue())) {
                    errors.add(new FieldError(field, "must be a finite number"));
                }
            } else if (!(value instanceof String) && !(value instanceof Boolean)) {
                errors.add(new FieldError(field, "must be a text, number or boolean value, not an object or a list"));
            }
        });
    }

    /** Removes control characters; optionally keeps line breaks and tabs, which are legitimate in a summary. */
    private static String removeControlCharacters(String text, boolean keepLineBreaks) {
        StringBuilder cleaned = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            boolean lineBreakOrTab = codePoint == '\n' || codePoint == '\r' || codePoint == '\t';
            if (!Character.isISOControl(codePoint) || (keepLineBreaks && lineBreakOrTab)) {
                cleaned.appendCodePoint(codePoint);
            }
        });
        return cleaned.toString();
    }

}
