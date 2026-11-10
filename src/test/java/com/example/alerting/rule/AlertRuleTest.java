package com.example.alerting.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for the creation rules of {@link AlertRule}. */
class AlertRuleTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    private static AlertRule rule(int minSeverity, int cooldownSeconds, Set<Long> destinations) {
        return new AlertRule(3L, "Big quakes", "Strong earthquakes in East Asia", "DISASTER", minSeverity,
                "{\"regions\":[\"JP\"]}", cooldownSeconds, destinations, NOW);
    }

    @Test
    void aNewRuleIsEnabledAndKeepsTheGivenFields() {
        AlertRule rule = rule(4, 300, Set.of(1L, 2L));

        assertThat(rule.getId()).isNull();
        assertThat(rule.getUserId()).isEqualTo(3L);
        assertThat(rule.getName()).isEqualTo("Big quakes");
        assertThat(rule.getDescription()).isEqualTo("Strong earthquakes in East Asia");
        assertThat(rule.getCategory()).isEqualTo("DISASTER");
        assertThat(rule.getMinSeverity()).isEqualTo(4);
        assertThat(rule.getCriteria()).isEqualTo("{\"regions\":[\"JP\"]}");
        assertThat(rule.getCooldownSeconds()).isEqualTo(300);
        assertThat(rule.getDestinationIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(rule.isEnabled()).isTrue();
        assertThat(rule.getLastTriggeredAt()).isNull();
        assertThat(rule.getDeletedAt()).isNull();
        assertThat(rule.getCreatedAt()).isEqualTo(NOW);
        assertThat(rule.getUpdatedAt()).isEqualTo(NOW);
        assertThat(rule.getVersion()).isZero();
    }

    @Test
    void optionalFieldsAreNormalized() {
        AlertRule rule = new AlertRule(1L, "  Name ", "   ", " NEWS ", 1, null, 0, Set.of(9L), NOW);

        assertThat(rule.getName()).isEqualTo("Name");
        assertThat(rule.getDescription()).isNull();
        assertThat(rule.getCategory()).isEqualTo("NEWS");
        assertThat(rule.getCriteria()).isEqualTo("{}");
        assertThat(new AlertRule(1L, "n", null, "NEWS", 1, "   ", 0, Set.of(9L), NOW).getCriteria()).isEqualTo("{}");
    }

    @ParameterizedTest(name = "minimum severity {0} is accepted")
    @ValueSource(ints = {1, 3, 5})
    void minimumSeverityInsideTheScaleIsAccepted(int minSeverity) {
        assertThat(rule(minSeverity, 0, Set.of(1L)).getMinSeverity()).isEqualTo(minSeverity);
    }

    @ParameterizedTest(name = "minimum severity {0} is rejected")
    @ValueSource(ints = {0, 6, -1})
    void minimumSeverityOutsideTheScaleIsRejected(int minSeverity) {
        assertThatThrownBy(() -> rule(minSeverity, 0, Set.of(1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minSeverity must be between 1 and 5");
    }

    @ParameterizedTest(name = "cooldown {0} s is accepted")
    @ValueSource(ints = {0, 1, 300, AlertRule.MAX_COOLDOWN_SECONDS})
    void cooldownUpToOneDayIsAccepted(int cooldown) {
        assertThat(rule(1, cooldown, Set.of(1L)).getCooldownSeconds()).isEqualTo(cooldown);
    }

    @ParameterizedTest(name = "cooldown {0} s is rejected")
    @ValueSource(ints = {-1, AlertRule.MAX_COOLDOWN_SECONDS + 1})
    void cooldownOutsideTheRangeIsRejected(int cooldown) {
        assertThatThrownBy(() -> rule(1, cooldown, Set.of(1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cooldownSeconds must be between 0 and 86400");
    }

    @Test
    void aRuleNeedsAtLeastOneDestination() {
        assertThatThrownBy(() -> rule(1, 0, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("destinationIds must contain at least one destination");
    }

    @Test
    void aMissingDestinationSetIsRejected() {
        assertThatThrownBy(() -> rule(1, 0, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void theDestinationIdsCannotBeChangedFromOutside() {
        Set<Long> source = new HashSet<>(Set.of(1L));
        AlertRule rule = rule(1, 0, source);
        source.add(2L);

        assertThat(rule.getDestinationIds()).containsExactly(1L);
        Set<Long> exposed = rule.getDestinationIds();
        assertThatThrownBy(() -> exposed.add(5L)).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest(name = "name \"{0}\" is rejected")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void aBlankNameIsRejected(String name) {
        assertThatThrownBy(() -> new AlertRule(1L, name, null, "NEWS", 1, null, 0, Set.of(1L), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
    }

    @Test
    void aBlankCategoryIsRejected() {
        assertThatThrownBy(() -> new AlertRule(1L, "n", null, " ", 1, null, 0, Set.of(1L), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("category must not be blank");
    }

    @Test
    void theCreationTimeIsRequired() {
        assertThatThrownBy(() -> new AlertRule(1L, "n", null, "NEWS", 1, null, 0, Set.of(1L), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("now");
    }
}
