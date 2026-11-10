package com.example.alerting.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Docs/02 section 6.2: market move to severity, and the derived attributes. */
class MarketSeverityNormalizerTest {

    private final MarketSeverityNormalizer normalizer = new MarketSeverityNormalizer(List.of(1.0, 2.0, 5.0, 10.0));

    @ParameterizedTest
    @CsvSource({"0.0,1", "0.99,1", "-0.99,1", "1.0,2", "-1.0,2", "1.99,2", "2.0,3", "-4.99,3", "5.0,4", "-9.99,4",
            "10.0,5", "-10.0,5", "-25.0,5"})
    void severityFollowsTheSizeOfTheMoveNotItsDirection(double changePct, int expectedSeverity) {
        assertThat(normalizer.derive(Map.of("changePct", changePct)).orElseThrow().severity())
                .isEqualTo(expectedSeverity);
    }

    @Test
    void deriveTheSizeAndTheDirectionOfTheMove() {
        Derivation down = normalizer.derive(Map.of("changePct", -6.3)).orElseThrow();
        Derivation up = normalizer.derive(Map.of("changePct", 2)).orElseThrow();

        assertThat(down.derivedAttributes()).containsEntry("moveMagnitudePct", 6.3).containsEntry("direction", "DOWN");
        assertThat(up.derivedAttributes()).containsEntry("moveMagnitudePct", 2.0).containsEntry("direction", "UP");
    }

    @Test
    void aMoveOfExactlyZeroIsFlat() {
        Derivation flat = normalizer.derive(Map.of("changePct", 0.0)).orElseThrow();

        assertThat(flat.severity()).isEqualTo(1);
        assertThat(flat.derivedAttributes()).containsEntry("moveMagnitudePct", 0.0).containsEntry("direction", "FLAT");
    }

    @Test
    void withoutAUsableChangeNothingCanBeDerived() {
        assertThat(normalizer.derive(Map.of())).isEmpty();
        assertThat(normalizer.derive(Map.of("changePct", "6.3"))).isEmpty();
        assertThat(normalizer.derive(Map.of("changePct", Double.NaN))).isEmpty();
        assertThat(normalizer.derive(Map.of("changePct", Double.NEGATIVE_INFINITY))).isEmpty();
        assertThat(normalizer.derive(Map.of("price", 100.0))).isEmpty();
    }

    @Test
    void servesTheMarketCategoryAndNamesWhatItNeeds() {
        assertThat(normalizer.category()).isEqualTo("MARKET");
        assertThat(normalizer.requirement()).isEqualTo("the numeric attribute 'changePct'");
    }

    @Test
    void invalidThresholdsAreRejectedAtConstructionNamingTheConfigurationKey() {
        assertThatThrownBy(() -> new MarketSeverityNormalizer(List.of(1.0, 2.0, 3.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alerting.severity.market.change-pct-thresholds");
    }
}
