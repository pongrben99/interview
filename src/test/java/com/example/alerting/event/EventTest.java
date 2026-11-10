package com.example.alerting.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for the creation rules of {@link Event}, {@link EventSource} and {@link EventCategory}. */
class EventTest {

    private static final Instant OCCURRED = Instant.parse("2026-11-10T08:12:00Z");
    private static final Instant RECEIVED = Instant.parse("2026-11-10T08:12:05Z");

    private static Event event(int severity, String title, String region) {
        return new Event(7L, "usgs:us0001", "DISASTER", severity, title, "Shallow quake", "https://example.com/e/1",
                region, OCCURRED, RECEIVED, "{\"magnitude\":6.4}");
    }

    @Test
    void aNewEventKeepsAllBusinessFieldsAndStartsAsReceived() {
        Event event = event(4, "M 6.4 earthquake", "JP");

        assertThat(event.getId()).isNull();
        assertThat(event.getSourceId()).isEqualTo(7L);
        assertThat(event.getExternalId()).isEqualTo("usgs:us0001");
        assertThat(event.getCategory()).isEqualTo("DISASTER");
        assertThat(event.getSeverity()).isEqualTo(4);
        assertThat(event.getTitle()).isEqualTo("M 6.4 earthquake");
        assertThat(event.getSummary()).isEqualTo("Shallow quake");
        assertThat(event.getUrl()).isEqualTo("https://example.com/e/1");
        assertThat(event.getRegion()).isEqualTo("JP");
        assertThat(event.getOccurredAt()).isEqualTo(OCCURRED);
        assertThat(event.getReceivedAt()).isEqualTo(RECEIVED);
        assertThat(event.getAttributes()).isEqualTo("{\"magnitude\":6.4}");
        assertThat(event.getProcessingStatus()).isEqualTo(EventProcessingStatus.RECEIVED);
        assertThat(event.getMatchedAt()).isNull();
        assertThat(event.getMatchCount()).isZero();
        assertThat(event.getMatchAttempts()).isZero();
        assertThat(event.getMatchError()).isNull();
    }

    @ParameterizedTest(name = "severity {0} is accepted")
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void severityOneToFiveIsAccepted(int severity) {
        assertThat(event(severity, "title", "JP").getSeverity()).isEqualTo(severity);
    }

    @ParameterizedTest(name = "severity {0} is rejected")
    @ValueSource(ints = {0, 6, -1})
    void severityOutsideTheScaleIsRejected(int severity) {
        assertThatThrownBy(() -> event(severity, "title", "JP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("severity must be between 1 and 5");
    }

    @Test
    void theRegionIsTrimmedAndUpperCased() {
        assertThat(event(3, "title", "  us-ca ").getRegion()).isEqualTo("US-CA");
    }

    @ParameterizedTest(name = "region \"{0}\" means no region")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void aBlankRegionBecomesNull(String region) {
        assertThat(event(3, "title", region).getRegion()).isNull();
    }

    @Test
    void optionalTextsAreNormalizedAndAttributesDefaultToAnEmptyObject() {
        Event event = new Event(1L, " ext-1 ", " NEWS ", 3, "  Headline  ", "   ", "", null, OCCURRED, RECEIVED, null);

        assertThat(event.getExternalId()).isEqualTo("ext-1");
        assertThat(event.getCategory()).isEqualTo("NEWS");
        assertThat(event.getTitle()).isEqualTo("Headline");
        assertThat(event.getSummary()).isNull();
        assertThat(event.getUrl()).isNull();
        assertThat(event.getAttributes()).isEqualTo("{}");
    }

    @Test
    void blankAttributesAlsoDefaultToAnEmptyObject() {
        Event event = new Event(1L, "ext-1", "NEWS", 3, "Headline", null, null, null, OCCURRED, RECEIVED, "  ");

        assertThat(event.getAttributes()).isEqualTo("{}");
    }

    @ParameterizedTest(name = "title \"{0}\" is rejected")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void aBlankTitleIsRejected(String title) {
        assertThatThrownBy(() -> event(3, title, "JP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title must not be blank");
    }

    @Test
    void aBlankExternalIdIsRejected() {
        assertThatThrownBy(() -> new Event(1L, " ", "NEWS", 3, "t", null, null, null, OCCURRED, RECEIVED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("externalId must not be blank");
    }

    @Test
    void aBlankCategoryIsRejected() {
        assertThatThrownBy(() -> new Event(1L, "e", "", 3, "t", null, null, null, OCCURRED, RECEIVED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("category must not be blank");
    }

    @Test
    void theTimestampsAreRequired() {
        assertThatThrownBy(() -> new Event(1L, "e", "NEWS", 3, "t", null, null, null, null, RECEIVED, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("occurredAt");
        assertThatThrownBy(() -> new Event(1L, "e", "NEWS", 3, "t", null, null, null, OCCURRED, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("receivedAt");
    }

    @Test
    void aNewEventSourceHasNeverBeenPolled() {
        EventSource source = new EventSource(" usgs-earthquakes ", " USGS ", SourceType.POLL, false, "{\"x\":1}", RECEIVED);

        assertThat(source.getId()).isNull();
        assertThat(source.getCode()).isEqualTo("usgs-earthquakes");
        assertThat(source.getName()).isEqualTo("USGS");
        assertThat(source.getType()).isEqualTo(SourceType.POLL);
        assertThat(source.isEnabled()).isFalse();
        assertThat(source.getConfig()).isEqualTo("{\"x\":1}");
        assertThat(source.getCreatedAt()).isEqualTo(RECEIVED);
        assertThat(source.getLastSuccessAt()).isNull();
        assertThat(source.getLastErrorAt()).isNull();
        assertThat(source.getLastError()).isNull();
    }

    @ParameterizedTest(name = "source config \"{0}\" defaults to an empty object")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void anEventSourceWithoutConfigGetsAnEmptyObject(String config) {
        assertThat(new EventSource("manual", "Manual", SourceType.PUSH, true, config, RECEIVED).getConfig())
                .isEqualTo("{}");
    }

    @Test
    void anEventSourceNeedsACodeANameATypeAndACreationTime() {
        assertThatThrownBy(() -> new EventSource(" ", "n", SourceType.PUSH, true, null, RECEIVED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("code must not be blank");
        assertThatThrownBy(() -> new EventSource("c", "", SourceType.PUSH, true, null, RECEIVED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
        assertThatThrownBy(() -> new EventSource("c", "n", null, true, null, RECEIVED))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("type");
        assertThatThrownBy(() -> new EventSource("c", "n", SourceType.PUSH, true, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("createdAt");
    }

    @Test
    void anEventCategoryKeepsItsFieldsAndNormalizesTheDescription() {
        EventCategory category = new EventCategory(" HEALTH ", " Health ", "   ", true);

        assertThat(category.getCode()).isEqualTo("HEALTH");
        assertThat(category.getDisplayName()).isEqualTo("Health");
        assertThat(category.getDescription()).isNull();
        assertThat(category.isEnabled()).isTrue();
        assertThat(new EventCategory("A", "B", " about ", false).getDescription()).isEqualTo("about");
    }

    @Test
    void anEventCategoryNeedsACodeAndADisplayName() {
        assertThatThrownBy(() -> new EventCategory("", "Name", null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("code must not be blank");
        assertThatThrownBy(() -> new EventCategory("CODE", " ", null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("displayName must not be blank");
    }
}
