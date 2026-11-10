package com.example.alerting.destination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Tests for the creation rules of {@link ChannelDestination}. */
class ChannelDestinationTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:30:00Z");

    @Test
    void aNewDestinationIsActiveAndKeepsTheGivenFields() {
        ChannelDestination destination = new ChannelDestination(
                5L, "EMAIL", "My inbox", "{\"address\":\"alice@example.com\"}", "alice@example.com", NOW);

        assertThat(destination.getId()).isNull();
        assertThat(destination.getUserId()).isEqualTo(5L);
        assertThat(destination.getChannelType()).isEqualTo("EMAIL");
        assertThat(destination.getLabel()).isEqualTo("My inbox");
        assertThat(destination.getConfig()).isEqualTo("{\"address\":\"alice@example.com\"}");
        assertThat(destination.getDestinationKey()).isEqualTo("alice@example.com");
        assertThat(destination.getStatus()).isEqualTo(DestinationStatus.ACTIVE);
        assertThat(destination.getDeletedAt()).isNull();
        assertThat(destination.getCreatedAt()).isEqualTo(NOW);
        assertThat(destination.getUpdatedAt()).isEqualTo(NOW);
        assertThat(destination.getVersion()).isZero();
    }

    @Test
    void textFieldsAreTrimmed() {
        ChannelDestination destination = new ChannelDestination(1L, " SLACK ", " ops ", " {} ", " key ", NOW);

        assertThat(destination.getChannelType()).isEqualTo("SLACK");
        assertThat(destination.getLabel()).isEqualTo("ops");
        assertThat(destination.getConfig()).isEqualTo("{}");
        assertThat(destination.getDestinationKey()).isEqualTo("key");
    }

    @Test
    void blankRequiredTextIsRejected() {
        assertThatThrownBy(() -> new ChannelDestination(1L, " ", "l", "{}", "k", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("channelType must not be blank");
        assertThatThrownBy(() -> new ChannelDestination(1L, "EMAIL", "", "{}", "k", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("label must not be blank");
        assertThatThrownBy(() -> new ChannelDestination(1L, "EMAIL", "l", null, "k", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("config must not be blank");
        assertThatThrownBy(() -> new ChannelDestination(1L, "EMAIL", "l", "{}", "  ", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("destinationKey must not be blank");
    }

    @Test
    void theCreationTimeIsRequired() {
        assertThatThrownBy(() -> new ChannelDestination(1L, "EMAIL", "l", "{}", "k", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("now");
    }
}
