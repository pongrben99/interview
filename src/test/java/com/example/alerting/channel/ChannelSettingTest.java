package com.example.alerting.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Tests for the creation rules of {@link ChannelSetting}. */
class ChannelSettingTest {

    private static final Instant NOW = Instant.parse("2026-11-10T11:00:00Z");

    @Test
    void theSettingsOfANewChannelAreEnabledWithTheDefaultRetryBudget() {
        ChannelSetting setting = new ChannelSetting(" SLACK ", NOW);

        assertThat(setting.getChannelType()).isEqualTo("SLACK");
        assertThat(setting.isEnabled()).isTrue();
        assertThat(setting.getMaxAttempts()).isNull();
        assertThat(setting.getUpdatedAt()).isEqualTo(NOW);
        assertThat(setting.getUpdatedBy()).isNull();
    }

    @Test
    void aBlankChannelTypeIsRejected() {
        assertThatThrownBy(() -> new ChannelSetting("  ", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("channelType must not be blank");
    }

    @Test
    void theTimeIsRequired() {
        assertThatThrownBy(() -> new ChannelSetting("EMAIL", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("now");
    }
}
