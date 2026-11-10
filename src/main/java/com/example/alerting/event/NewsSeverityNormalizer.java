package com.example.alerting.event;

import com.example.alerting.shared.Guards;
import java.util.Map;
import java.util.Optional;

/**
 * Severity of a news item from three optional flags: {@code breaking}, {@code developing} and
 * {@code minor}.
 *
 * <p>With the default levels: breaking is 4, developing is 3, minor is 1, and a news item with none of
 * the flags is 2. If several flags are set the most urgent wins (breaking, then developing, then minor).
 * News always yields a severity, because the flags are optional.
 */
public final class NewsSeverityNormalizer implements SeverityNormalizer {

    /** The category served by this normalizer. */
    public static final String CATEGORY = "NEWS";

    static final String BREAKING = "breaking";
    static final String DEVELOPING = "developing";
    static final String MINOR = "minor";

    private final int breaking;
    private final int developing;
    private final int minor;
    private final int standard;

    /**
     * Creates the normalizer.
     *
     * @param breaking the level of a breaking item
     * @param developing the level of a developing story
     * @param minor the level of a minor item
     * @param standard the level of an item with no flag
     * @throws IllegalArgumentException if a level is outside the 1 to 5 scale
     */
    public NewsSeverityNormalizer(int breaking, int developing, int minor, int standard) {
        this.breaking = Guards.requireInRange(breaking, Severity.MIN_LEVEL, Severity.MAX_LEVEL, "news breaking level");
        this.developing = Guards.requireInRange(developing, Severity.MIN_LEVEL, Severity.MAX_LEVEL, "news developing level");
        this.minor = Guards.requireInRange(minor, Severity.MIN_LEVEL, Severity.MAX_LEVEL, "news minor level");
        this.standard = Guards.requireInRange(standard, Severity.MIN_LEVEL, Severity.MAX_LEVEL, "news standard level");
    }

    @Override
    public String category() {
        return CATEGORY;
    }

    @Override
    public String requirement() {
        return "the boolean attributes '" + BREAKING + "', '" + DEVELOPING + "' or '" + MINOR + "'";
    }

    @Override
    public Optional<Derivation> derive(Map<String, Object> attributes) {
        int level;
        if (Attributes.isTrue(attributes, BREAKING)) {
            level = breaking;
        } else if (Attributes.isTrue(attributes, DEVELOPING)) {
            level = developing;
        } else if (Attributes.isTrue(attributes, MINOR)) {
            level = minor;
        } else {
            level = standard;
        }
        return Optional.of(new Derivation(level, Map.of()));
    }
}
