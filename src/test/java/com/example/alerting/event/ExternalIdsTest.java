package com.example.alerting.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Derived external ids (docs/02 section 6.5): same content, same id; any difference, a different id. */
class ExternalIdsTest {

    private static final Instant WHEN = Instant.parse("2026-11-10T08:59:00Z");
    private static final String TITLE = "M 6.4 earthquake near Hualien, Taiwan";

    @Test
    void theDerivationIsPinnedToKnownValues() {
        // These constants were computed independently (SHA-256 of the unit-separator joined fields). A change
        // here means every previously derived id changes and old events would no longer be recognised.
        assertThat(ExternalIds.derive("DISASTER", TITLE, WHEN, "TW"))
                .isEqualTo("h:c904c71b97f970f2ddf0048f8a39afeab6b552bfd9c9c6194126f19ae152be0c");
        assertThat(ExternalIds.derive("DISASTER", "No region", WHEN, null))
                .isEqualTo("h:6bfb8717e6bf8d54a5a20161b83be6290b7386126e0edabcab83ffa318505cb6");
    }

    @Test
    void theIdIsThePrefixFollowedBy64LowerCaseHexDigits() {
        assertThat(ExternalIds.derive("DISASTER", TITLE, WHEN, "TW")).matches("h:[0-9a-f]{64}");
    }

    @Test
    void theSameContentAlwaysGivesTheSameId() {
        assertThat(ExternalIds.derive("DISASTER", TITLE, WHEN, "TW"))
                .isEqualTo(ExternalIds.derive("DISASTER", TITLE, WHEN, "TW"));
    }

    @Test
    void theInstantCountsNotItsSpelling() {
        assertThat(ExternalIds.derive("DISASTER", TITLE, Instant.parse("2026-11-10T08:59:00.000Z"), "TW"))
                .isEqualTo(ExternalIds.derive("DISASTER", TITLE, Instant.parse("2026-11-10T09:59:00+01:00"), "TW"));
    }

    @Test
    void theRegionCaseDoesNotMatterBecauseRegionsAreStoredUpperCase() {
        assertThat(ExternalIds.derive("DISASTER", TITLE, WHEN, "us-ca"))
                .isEqualTo(ExternalIds.derive("DISASTER", TITLE, WHEN, "US-CA"));
    }

    @Test
    void everyIdentifyingFieldChangesTheId() {
        String base = ExternalIds.derive("DISASTER", TITLE, WHEN, "TW");

        assertThat(ExternalIds.derive("MARKET", TITLE, WHEN, "TW")).isNotEqualTo(base);
        assertThat(ExternalIds.derive("DISASTER", TITLE + "!", WHEN, "TW")).isNotEqualTo(base);
        assertThat(ExternalIds.derive("DISASTER", TITLE, WHEN.plusSeconds(1), "TW")).isNotEqualTo(base);
        assertThat(ExternalIds.derive("DISASTER", TITLE, WHEN, "JP")).isNotEqualTo(base);
        assertThat(ExternalIds.derive("DISASTER", TITLE, WHEN, null)).isNotEqualTo(base);
    }

    @Test
    void theBoundariesBetweenFieldsMatter() {
        // Without a separator these two would hash the same text.
        assertThat(ExternalIds.derive("DIS", "ASTER", WHEN, null))
                .isNotEqualTo(ExternalIds.derive("DISASTER", "", WHEN, null));
        assertThat(ExternalIds.derive("DISASTER", "A|B", WHEN, null))
                .isNotEqualTo(ExternalIds.derive("DISASTER|A", "B", WHEN, null));
    }
}
