package com.example.alerting.event;

import java.util.Map;
import java.util.OptionalDouble;

/**
 * Typed reads from an event's attribute map for the severity normalizers.
 *
 * <p>Attributes arrive as loose JSON scalars, so a value may be missing or of the wrong type; these
 * helpers treat both as "not there" instead of failing.
 */
final class Attributes {

    private Attributes() {
    }

    /**
     * Reads a finite number.
     *
     * @param attributes the attribute map
     * @param key the attribute name
     * @return the value, or empty if the key is absent, not a number, or not finite
     */
    static OptionalDouble finiteNumber(Map<String, Object> attributes, String key) {
        if (attributes.get(key) instanceof Number number) {
            double value = number.doubleValue();
            if (Double.isFinite(value)) {
                return OptionalDouble.of(value);
            }
        }
        return OptionalDouble.empty();
    }

    /**
     * Reads a flag.
     *
     * @param attributes the attribute map
     * @param key the attribute name
     * @return {@code true} only if the value is the boolean {@code true}; absent or any other value is false
     */
    static boolean isTrue(Map<String, Object> attributes, String key) {
        return Boolean.TRUE.equals(attributes.get(key));
    }
}
