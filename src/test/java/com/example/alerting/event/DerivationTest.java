package com.example.alerting.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DerivationTest {

    @Test
    void holdsASeverityAndDerivedAttributes() {
        Derivation derivation = new Derivation(3, Map.of("direction", "UP"));

        assertThat(derivation.severity()).isEqualTo(3);
        assertThat(derivation.derivedAttributes()).containsEntry("direction", "UP");
    }

    @Test
    void theSeverityMustBeOnTheScale() {
        assertThatThrownBy(() -> new Derivation(0, Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Derivation(6, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theDerivedAttributesAreACopyThatCannotChange() {
        Map<String, Object> source = new HashMap<>(Map.of("direction", "UP"));
        Derivation derivation = new Derivation(3, source);

        source.put("direction", "DOWN");

        assertThat(derivation.derivedAttributes()).containsEntry("direction", "UP");
        assertThatThrownBy(() -> derivation.derivedAttributes().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
