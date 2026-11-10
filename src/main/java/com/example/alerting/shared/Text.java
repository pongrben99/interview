package com.example.alerting.shared;

/**
 * Text helpers for values that end up in size-limited database columns or optional fields.
 */
public final class Text {

    /** Marker appended to text that had to be shortened. */
    static final String ELLIPSIS = "…";

    private Text() {
    }

    /**
     * Shortens text so it fits a size-limited column, marking the cut with an ellipsis.
     *
     * <p>The result is never longer than {@code maxLength} characters and a surrogate pair (for
     * example an emoji) is never cut in half. Text that already fits is returned unchanged.
     *
     * @param value the text to shorten, may be {@code null}
     * @param maxLength the longest allowed result, at least 1
     * @return the original text if it fits, otherwise a shortened copy; {@code null} for {@code null}
     * @throws IllegalArgumentException if {@code maxLength} is below 1
     */
    public static String truncate(String value, int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be at least 1 but was " + maxLength);
        }
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        int cut = maxLength - 1;
        if (cut > 0 && Character.isHighSurrogate(value.charAt(cut - 1))) {
            cut--;
        }
        return value.substring(0, cut) + ELLIPSIS;
    }

    /**
     * Makes untrusted text safe to repeat in an error message or a log line: it is shortened and every
     * control character (line breaks included) is replaced by a question mark.
     *
     * @param value the text, may be {@code null}
     * @param maxLength the longest allowed result before the ellipsis, at least 1
     * @return the sanitized text; the word {@code null} for {@code null}
     * @throws IllegalArgumentException if {@code maxLength} is below 1
     */
    public static String printable(String value, int maxLength) {
        if (value == null) {
            return "null";
        }
        StringBuilder printable = new StringBuilder();
        truncate(value, maxLength).codePoints()
                .forEach(codePoint -> printable.appendCodePoint(Character.isISOControl(codePoint) ? '?' : codePoint));
        return printable.toString();
    }

    /**
     * Normalizes optional text: surrounding whitespace is removed and blank text becomes {@code null}.
     *
     * @param value the text to normalize, may be {@code null}
     * @return the trimmed text, or {@code null} if the input was null or blank
     */
    public static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
