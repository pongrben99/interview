package com.example.alerting.shared;

/**
 * Small, side-effect-free argument checks used by the constructors of the domain entities.
 *
 * <p>Every check throws {@link IllegalArgumentException} with a message that names the offending
 * field, so a rejected value can be reported to the caller without further work. A {@code null}
 * text counts as blank.
 */
public final class Guards {

    private Guards() {
    }

    /**
     * Requires text that is neither {@code null} nor blank.
     *
     * @param value the text to check
     * @param field the field name used in the error message
     * @return the text with surrounding whitespace removed
     * @throws IllegalArgumentException if the text is null, empty or whitespace only
     */
    public static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    /**
     * Requires a number inside a closed range.
     *
     * @param value the number to check
     * @param min the smallest allowed value, inclusive
     * @param max the largest allowed value, inclusive
     * @param field the field name used in the error message
     * @return the number, unchanged
     * @throws IllegalArgumentException if the number lies outside {@code [min, max]}
     */
    public static int requireInRange(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    field + " must be between " + min + " and " + max + " but was " + value);
        }
        return value;
    }

    /**
     * Requires a number that is not below a lower bound.
     *
     * @param value the number to check
     * @param min the smallest allowed value, inclusive
     * @param field the field name used in the error message
     * @return the number, unchanged
     * @throws IllegalArgumentException if the number is below {@code min}
     */
    public static int requireAtLeast(int value, int min, String field) {
        if (value < min) {
            throw new IllegalArgumentException(field + " must be at least " + min + " but was " + value);
        }
        return value;
    }
}
