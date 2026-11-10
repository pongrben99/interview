package com.example.alerting.event;

import static com.example.alerting.support.NewEventBuilder.anEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.alerting.shared.FieldError;
import com.example.alerting.shared.ValidationException;
import com.example.alerting.support.TestEvents;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Severity, derived attributes and external ids (docs/02 sections 6.2 and 6.5, step 4). */
class EventNormalizerTest {

    private final EventNormalizer normalizer = new EventNormalizer(List.of(
            new DisasterSeverityNormalizer(List.of(4.0, 5.0, 6.0, 7.0)),
            new MarketSeverityNormalizer(List.of(1.0, 2.0, 5.0, 10.0)),
            new NewsSeverityNormalizer(4, 3, 1, 2)));

    // ------------------------------------------------------------------ severity

    @Test
    void theSeverityIsDerivedWhenNotSupplied() {
        assertThat(normalizer.normalize(anEvent().build()).severity()).isEqualTo(4); // magnitude 6.4
    }

    @Test
    void aSuppliedSeverityAlwaysWinsOverTheDerivedOne() {
        NormalizedEvent result = normalizer.normalize(anEvent().attribute("magnitude", 8.5).severity(2).build());

        assertThat(result.severity()).isEqualTo(2);
    }

    @Test
    void aSuppliedSeverityIsEnoughWhenNothingCanBeDerived() {
        NormalizedEvent result = normalizer.normalize(anEvent().noAttributes().severity(3).build());

        assertThat(result.severity()).isEqualTo(3);
        assertThat(result.attributes()).isEmpty();
    }

    @Test
    void aSeverityThatCannotBeDerivedNamesWhatToSupply() {
        NewEvent noMagnitude = anEvent().noAttributes().build();

        assertThatThrownBy(() -> normalizer.normalize(noMagnitude))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getErrors()).containsExactly(
                        new FieldError("severity", "is required: it cannot be derived for category DISASTER without "
                                + "the numeric attribute 'magnitude'")));
    }

    @Test
    void aCategoryWithoutANormalizerRequiresASeverity() {
        NewEvent sports = anEvent().category("SPORTS").noAttributes().build();

        assertThatThrownBy(() -> normalizer.normalize(sports))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getErrors()).containsExactly(
                        new FieldError("severity", "is required for category SPORTS, which has no automatic severity")));
        assertThat(normalizer.normalize(anEvent().category("SPORTS").severity(3).build()).severity()).isEqualTo(3);
    }

    @Test
    void newsAlwaysHasASeverity() {
        NewEvent news = anEvent().category("NEWS").noAttributes().build();

        assertThat(normalizer.normalize(news).severity()).isEqualTo(2);
        assertThat(normalizer.normalize(anEvent().category("NEWS").attributes(Map.of("breaking", true)).build()).severity())
                .isEqualTo(4);
    }

    // ------------------------------------------------------------------ derived attributes

    @Test
    void derivedAttributesAreAddedToTheSendersAttributes() {
        NormalizedEvent result = normalizer.normalize(
                anEvent().category("MARKET").attributes(Map.of("symbol", "NVDA", "changePct", -6.3)).build());

        assertThat(result.severity()).isEqualTo(4);
        assertThat(result.attributes()).containsOnlyKeys("symbol", "changePct", "moveMagnitudePct", "direction")
                .containsEntry("moveMagnitudePct", 6.3)
                .containsEntry("direction", "DOWN");
    }

    @Test
    void derivedAttributesAreAddedEvenWhenTheSeverityWasSupplied() {
        NormalizedEvent result = normalizer.normalize(
                anEvent().category("MARKET").attributes(Map.of("changePct", 3.0)).severity(1).build());

        assertThat(result.severity()).isEqualTo(1);
        assertThat(result.attributes()).containsEntry("direction", "UP").containsEntry("moveMagnitudePct", 3.0);
    }

    @Test
    void aDerivedAttributeNeverOverwritesOneTheSenderSupplied() {
        Map<String, Object> supplied = new LinkedHashMap<>();
        supplied.put("changePct", -6.3);
        supplied.put("direction", "SIDEWAYS");
        supplied.put("moveMagnitudePct", 99);

        NormalizedEvent result = normalizer.normalize(anEvent().category("MARKET").attributes(supplied).build());

        assertThat(result.attributes()).containsEntry("direction", "SIDEWAYS").containsEntry("moveMagnitudePct", 99);
    }

    @Test
    void theSendersAttributeMapIsNotModified() {
        NewEvent event = anEvent().category("MARKET").attributes(Map.of("changePct", -6.3)).build();

        normalizer.normalize(event);

        assertThat(event.attributes()).containsOnlyKeys("changePct");
    }

    // ------------------------------------------------------------------ external id

    @Test
    void aSuppliedExternalIdIsKept() {
        assertThat(normalizer.normalize(anEvent().externalId("demo:quake-1").build()).externalId())
                .isEqualTo("demo:quake-1");
    }

    @Test
    void aMissingExternalIdIsDerivedFromTheContent() {
        NewEvent event = anEvent().externalId(null).region("tw").build();

        assertThat(normalizer.normalize(event).externalId())
                .isEqualTo(ExternalIds.derive("DISASTER", event.title(), event.occurredAt(), "TW"))
                .startsWith("h:");
    }

    @Test
    void twoIdenticalEventsWithoutIdsGetTheSameId() {
        assertThat(normalizer.normalize(anEvent().externalId(null).build()).externalId())
                .isEqualTo(normalizer.normalize(anEvent().externalId(null).build()).externalId());
        assertThat(normalizer.normalize(anEvent().externalId(null).build()).externalId())
                .isNotEqualTo(normalizer.normalize(anEvent().externalId(null).occurredAt(TestEvents.NOW).build()).externalId());
    }

    // ------------------------------------------------------------------ construction

    @Test
    void twoNormalizersForTheSameCategoryAreRejected() {
        List<SeverityNormalizer> twice = List.of(new NewsSeverityNormalizer(4, 3, 1, 2), new NewsSeverityNormalizer(5, 4, 2, 3));

        assertThatThrownBy(() -> new EventNormalizer(twice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Two severity normalizers are registered for category NEWS");
    }

    @Test
    void noNormalizersAtAllMeansEverySeverityMustBeSupplied() {
        EventNormalizer none = new EventNormalizer(List.of());

        assertThatThrownBy(() -> none.normalize(anEvent().build())).isInstanceOf(ValidationException.class);
        assertThat(none.normalize(anEvent().severity(2).build()).severity()).isEqualTo(2);
    }
}
