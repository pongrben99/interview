package com.example.alerting.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Docs/02 section 6.2: earthquake magnitude to severity, with the tsunami bonus. */
class DisasterSeverityNormalizerTest {

    private final DisasterSeverityNormalizer normalizer = new DisasterSeverityNormalizer(List.of(4.0, 5.0, 6.0, 7.0));

    @ParameterizedTest
    @CsvSource({"1.2,1", "3.9,1", "4.0,2", "4.9,2", "5.0,3", "5.9,3", "6.0,4", "6.4,4", "6.9,4", "7.0,5", "9.1,5"})
    void severityFollowsTheMagnitude(double magnitude, int expectedSeverity) {
        assertThat(severityOf(Map.of("magnitude", magnitude))).isEqualTo(expectedSeverity);
    }

    @Test
    void aTsunamiRaisesTheSeverityByOneUpToTheMaximum() {
        assertThat(severityOf(Map.of("magnitude", 3.0, "tsunami", true))).isEqualTo(2);
        assertThat(severityOf(Map.of("magnitude", 6.4, "tsunami", true))).isEqualTo(5);
        assertThat(severityOf(Map.of("magnitude", 7.8, "tsunami", true))).isEqualTo(5);
    }

    @Test
    void onlyTheBooleanTrueCountsAsATsunami() {
        assertThat(severityOf(Map.of("magnitude", 6.4, "tsunami", false))).isEqualTo(4);
        assertThat(severityOf(Map.of("magnitude", 6.4, "tsunami", "true"))).isEqualTo(4);
        assertThat(severityOf(Map.of("magnitude", 6.4, "tsunami", 1))).isEqualTo(4);
    }

    @Test
    void integerAndLongMagnitudesWork() {
        assertThat(severityOf(Map.of("magnitude", 7))).isEqualTo(5);
        assertThat(severityOf(Map.of("magnitude", 5L))).isEqualTo(3);
    }

    @Test
    void withoutAUsableMagnitudeNothingCanBeDerived() {
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("magnitude", null);
        List<Map<String, Object>> unusable = List.of(
                Map.of(),
                Map.of("magnitude", "6.4"),
                Map.of("magnitude", true),
                Map.of("magnitude", Double.NaN),
                Map.of("magnitude", Double.POSITIVE_INFINITY),
                withNull,
                Map.of("tsunami", true));

        for (Map<String, Object> attributes : unusable) {
            assertThat(normalizer.derive(attributes)).as("attributes %s", attributes).isEmpty();
        }
    }

    @Test
    void addsNoDerivedAttributes() {
        Optional<Derivation> derivation = normalizer.derive(Map.of("magnitude", 6.4));

        assertThat(derivation).isPresent();
        assertThat(derivation.get().derivedAttributes()).isEmpty();
    }

    @Test
    void servesTheDisasterCategoryAndNamesWhatItNeeds() {
        assertThat(normalizer.category()).isEqualTo("DISASTER");
        assertThat(normalizer.requirement()).isEqualTo("the numeric attribute 'magnitude'");
    }

    @Test
    void customThresholdsAreHonoured() {
        DisasterSeverityNormalizer sensitive = new DisasterSeverityNormalizer(List.of(2.0, 3.0, 4.0, 5.0));

        assertThat(sensitive.derive(Map.of("magnitude", 4.2)).orElseThrow().severity()).isEqualTo(4);
    }

    @Test
    void invalidThresholdsAreRejectedAtConstructionNamingTheConfigurationKey() {
        assertThatThrownBy(() -> new DisasterSeverityNormalizer(List.of(7.0, 6.0, 5.0, 4.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alerting.severity.disaster.magnitude-thresholds");
    }

    private int severityOf(Map<String, Object> attributes) {
        return normalizer.derive(attributes).orElseThrow().severity();
    }
}
