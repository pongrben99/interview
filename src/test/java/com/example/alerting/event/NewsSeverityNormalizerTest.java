package com.example.alerting.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Docs/02 section 6.2: news flags to severity. */
class NewsSeverityNormalizerTest {

    private final NewsSeverityNormalizer normalizer = new NewsSeverityNormalizer(4, 3, 1, 2);

    @Test
    void theFlagsDecideTheSeverity() {
        assertThat(severityOf(Map.of("breaking", true))).isEqualTo(4);
        assertThat(severityOf(Map.of("developing", true))).isEqualTo(3);
        assertThat(severityOf(Map.of("minor", true))).isEqualTo(1);
    }

    @Test
    void anItemWithoutFlagsIsStandard() {
        assertThat(severityOf(Map.of())).isEqualTo(2);
        assertThat(severityOf(Map.of("publisher", "Reuters", "breaking", false))).isEqualTo(2);
    }

    @Test
    void theMostUrgentFlagWins() {
        assertThat(severityOf(Map.of("breaking", true, "developing", true, "minor", true))).isEqualTo(4);
        assertThat(severityOf(Map.of("developing", true, "minor", true))).isEqualTo(3);
    }

    @Test
    void onlyTheBooleanTrueCountsAsAFlag() {
        assertThat(severityOf(Map.of("breaking", "true"))).isEqualTo(2);
        assertThat(severityOf(Map.of("breaking", 1))).isEqualTo(2);
    }

    @Test
    void newsAlwaysYieldsASeverityAndNoExtraAttributes() {
        Derivation derivation = normalizer.derive(Map.of()).orElseThrow();

        assertThat(derivation.derivedAttributes()).isEmpty();
    }

    @Test
    void servesTheNewsCategoryAndDescribesTheFlags() {
        assertThat(normalizer.category()).isEqualTo("NEWS");
        assertThat(normalizer.requirement()).contains("breaking", "developing", "minor");
    }

    @Test
    void theLevelsAreConfigurable() {
        NewsSeverityNormalizer custom = new NewsSeverityNormalizer(5, 4, 2, 3);

        assertThat(custom.derive(Map.of("breaking", true)).orElseThrow().severity()).isEqualTo(5);
        assertThat(custom.derive(Map.of()).orElseThrow().severity()).isEqualTo(3);
    }

    @Test
    void levelsOutsideTheScaleAreRejectedAtConstruction() {
        assertThatThrownBy(() -> new NewsSeverityNormalizer(6, 3, 1, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NewsSeverityNormalizer(4, 0, 1, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NewsSeverityNormalizer(4, 3, -1, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NewsSeverityNormalizer(4, 3, 1, 9)).isInstanceOf(IllegalArgumentException.class);
    }

    private int severityOf(Map<String, Object> attributes) {
        return normalizer.derive(attributes).orElseThrow().severity();
    }
}
