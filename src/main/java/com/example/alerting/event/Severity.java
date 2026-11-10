package com.example.alerting.event;

/**
 * How important an event is, on a scale from 1 to 5.
 *
 * <p>The scale is the same for every category, so a user rule can say "severity at least HIGH"
 * without knowing whether the event is an earthquake or a market move. Events and rules store the
 * numeric {@link #level()}; the names exist for messages and for the user interface.
 */
public enum Severity {

    /** Informational (1). */
    INFO(1),

    /** Low (2). */
    LOW(2),

    /** Medium (3). */
    MEDIUM(3),

    /** High (4). */
    HIGH(4),

    /** Critical (5). */
    CRITICAL(5);

    /** The smallest valid level. */
    public static final int MIN_LEVEL = 1;

    /** The largest valid level. */
    public static final int MAX_LEVEL = 5;

    private final int level;

    Severity(int level) {
        this.level = level;
    }

    /**
     * The numeric level stored in the database.
     *
     * @return a value from {@link #MIN_LEVEL} to {@link #MAX_LEVEL}
     */
    public int level() {
        return level;
    }

    /**
     * Looks up the severity for a stored numeric level.
     *
     * @param level the numeric level
     * @return the matching severity
     * @throws IllegalArgumentException if the level is not between {@link #MIN_LEVEL} and {@link #MAX_LEVEL}
     */
    public static Severity fromLevel(int level) {
        for (Severity severity : values()) {
            if (severity.level == level) {
                return severity;
            }
        }
        throw new IllegalArgumentException(
                "severity must be between " + MIN_LEVEL + " and " + MAX_LEVEL + " but was " + level);
    }
}
