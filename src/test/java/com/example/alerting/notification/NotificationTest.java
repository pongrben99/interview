package com.example.alerting.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for the factories of {@link Notification}: the two statuses a notification can be created in. */
class NotificationTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    @Test
    void aPendingNotificationIsDueImmediatelyWithAFreshAttemptCounter() {
        Notification notification = Notification.pending(11L, 12L, 13L, 14L, "SLACK", 5, NOW);

        assertThat(notification.getId()).isNull();
        assertThat(notification.getEventId()).isEqualTo(11L);
        assertThat(notification.getAlertRuleId()).isEqualTo(12L);
        assertThat(notification.getDestinationId()).isEqualTo(13L);
        assertThat(notification.getUserId()).isEqualTo(14L);
        assertThat(notification.getChannelType()).isEqualTo("SLACK");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getStatusReason()).isNull();
        assertThat(notification.getAttemptCount()).isZero();
        assertThat(notification.getMaxAttempts()).isEqualTo(5);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(notification.getLockedBy()).isNull();
        assertThat(notification.getLockedUntil()).isNull();
        assertThat(notification.getLastErrorCode()).isNull();
        assertThat(notification.getLastErrorMessage()).isNull();
        assertThat(notification.getCreatedAt()).isEqualTo(NOW);
        assertThat(notification.getUpdatedAt()).isEqualTo(NOW);
        assertThat(notification.getSentAt()).isNull();
        assertThat(notification.getVersion()).isZero();
    }

    @Test
    void aCooldownSuppressedNotificationIsSuppressedWithTheCooldownReason() {
        Notification notification = Notification.suppressedByCooldown(1L, 2L, 3L, 4L, " EMAIL ", 5, NOW);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SUPPRESSED);
        assertThat(notification.getStatusReason()).isEqualTo(NotificationStatusReason.COOLDOWN);
        assertThat(notification.getChannelType()).isEqualTo("EMAIL");
        assertThat(notification.getAttemptCount()).isZero();
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void bothFactoriesCreateInStatusesTheStateMachineAllowsAsStart() {
        assertThat(NotificationStateMachine.INITIAL_STATUSES).contains(
                Notification.pending(1L, 2L, 3L, 4L, "EMAIL", 1, NOW).getStatus(),
                Notification.suppressedByCooldown(1L, 2L, 3L, 4L, "EMAIL", 1, NOW).getStatus());
    }

    @ParameterizedTest(name = "max attempts {0} is rejected")
    @ValueSource(ints = {0, -1})
    void aRetryBudgetBelowOneIsRejected(int maxAttempts) {
        assertThatThrownBy(() -> Notification.pending(1L, 2L, 3L, 4L, "EMAIL", maxAttempts, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxAttempts must be at least 1 but was " + maxAttempts);
        assertThatThrownBy(() -> Notification.suppressedByCooldown(1L, 2L, 3L, 4L, "EMAIL", maxAttempts, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aBlankChannelTypeIsRejected() {
        assertThatThrownBy(() -> Notification.pending(1L, 2L, 3L, 4L, " ", 1, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("channelType must not be blank");
    }

    @Test
    void theCreationTimeIsRequired() {
        assertThatThrownBy(() -> Notification.pending(1L, 2L, 3L, 4L, "EMAIL", 1, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("now");
    }
}
