package com.example.alerting.notification;

import static com.example.alerting.notification.NotificationStatus.CANCELLED;
import static com.example.alerting.notification.NotificationStatus.DEAD;
import static com.example.alerting.notification.NotificationStatus.IN_FLIGHT;
import static com.example.alerting.notification.NotificationStatus.PENDING;
import static com.example.alerting.notification.NotificationStatus.RETRY_SCHEDULED;
import static com.example.alerting.notification.NotificationStatus.SENT;
import static com.example.alerting.notification.NotificationStatus.SUPPRESSED;
import static com.example.alerting.notification.NotificationTrigger.CANCEL_CLAIMED;
import static com.example.alerting.notification.NotificationTrigger.CANCEL_QUEUED;
import static com.example.alerting.notification.NotificationTrigger.CLAIM;
import static com.example.alerting.notification.NotificationTrigger.COMPLETE;
import static com.example.alerting.notification.NotificationTrigger.GIVE_UP;
import static com.example.alerting.notification.NotificationTrigger.REQUEUE;
import static com.example.alerting.notification.NotificationTrigger.RETRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for {@link NotificationStateMachine} and {@link NotificationTrigger}.
 *
 * <p>The expected table below is written independently of the production code, straight from
 * docs/02 section 8.1. The main test then checks every one of the 7 statuses against every one of
 * the 7 triggers (49 combinations): the 9 legal moves must lead where the table says, and the other
 * 40 must be rejected.
 */
class NotificationStateMachineTest {

    /** trigger -> (status it may be applied in -> status it leads to). */
    private static final Map<NotificationTrigger, Map<NotificationStatus, NotificationStatus>> LEGAL = Map.of(
            CLAIM, Map.of(PENDING, IN_FLIGHT, RETRY_SCHEDULED, IN_FLIGHT),
            COMPLETE, Map.of(IN_FLIGHT, SENT),
            RETRY, Map.of(IN_FLIGHT, RETRY_SCHEDULED),
            GIVE_UP, Map.of(IN_FLIGHT, DEAD),
            CANCEL_CLAIMED, Map.of(IN_FLIGHT, CANCELLED),
            CANCEL_QUEUED, Map.of(PENDING, CANCELLED, RETRY_SCHEDULED, CANCELLED),
            REQUEUE, Map.of(DEAD, PENDING));

    static Stream<Arguments> everyStatusWithEveryTrigger() {
        return EnumSet.allOf(NotificationStatus.class).stream().flatMap(status ->
                EnumSet.allOf(NotificationTrigger.class).stream().map(trigger -> Arguments.of(
                        status, trigger, LEGAL.get(trigger).get(status))));
    }

    @ParameterizedTest(name = "{1} in {0} -> {2}")
    @MethodSource("everyStatusWithEveryTrigger")
    void everyCombinationIsEitherLegalOrRejected(NotificationStatus from, NotificationTrigger trigger,
                                                 NotificationStatus expected) {
        if (expected != null) {
            assertThat(NotificationStateMachine.canApply(from, trigger)).isTrue();
            assertThat(NotificationStateMachine.apply(from, trigger)).isEqualTo(expected);
        } else {
            assertThat(NotificationStateMachine.canApply(from, trigger)).isFalse();
            assertThatThrownBy(() -> NotificationStateMachine.apply(from, trigger))
                    .isInstanceOf(IllegalStateTransitionException.class)
                    .hasMessage("Cannot apply " + trigger + " to a notification in status " + from)
                    .satisfies(e -> {
                        IllegalStateTransitionException exception = (IllegalStateTransitionException) e;
                        assertThat(exception.getFrom()).isEqualTo(from);
                        assertThat(exception.getTrigger()).isEqualTo(trigger);
                    });
        }
    }

    @Test
    void theExpectedTableHasNineLegalMovesAndCoversEveryTrigger() {
        assertThat(LEGAL).hasSize(NotificationTrigger.values().length);
        assertThat(LEGAL.values().stream().mapToInt(Map::size).sum()).isEqualTo(9);
    }

    @Test
    void theTriggersDescribeExactlyTheExpectedTable() {
        for (NotificationTrigger trigger : NotificationTrigger.values()) {
            assertThat(trigger.allowedFrom()).containsExactlyInAnyOrderElementsOf(LEGAL.get(trigger).keySet());
            assertThat(LEGAL.get(trigger).values()).containsOnly(trigger.target());
        }
    }

    @Test
    void sentCancelledAndSuppressedNotificationsCanNeverMoveAgain() {
        for (NotificationStatus finalStatus : Set.of(SENT, CANCELLED, SUPPRESSED)) {
            for (NotificationTrigger trigger : NotificationTrigger.values()) {
                assertThat(NotificationStateMachine.canApply(finalStatus, trigger))
                        .as("%s in %s", trigger, finalStatus)
                        .isFalse();
            }
        }
    }

    @Test
    void onlyAnAdminRequeueCanMoveADeadNotification() {
        for (NotificationTrigger trigger : NotificationTrigger.values()) {
            assertThat(NotificationStateMachine.canApply(DEAD, trigger)).isEqualTo(trigger == REQUEUE);
        }
    }

    @Test
    void aNotificationBeingDeliveredCanBeCancelledOnlyByTheDispatcherGuard() {
        assertThat(NotificationStateMachine.canApply(IN_FLIGHT, CANCEL_CLAIMED)).isTrue();
        assertThat(NotificationStateMachine.canApply(IN_FLIGHT, CANCEL_QUEUED)).isFalse();
    }

    @Test
    void aQueuedNotificationCanBeCancelledButNotByTheDispatcherGuard() {
        for (NotificationStatus queued : Set.of(PENDING, RETRY_SCHEDULED)) {
            assertThat(NotificationStateMachine.canApply(queued, CANCEL_QUEUED)).isTrue();
            assertThat(NotificationStateMachine.canApply(queued, CANCEL_CLAIMED)).isFalse();
        }
    }

    @Test
    void notificationsStartAsPendingOrSuppressed() {
        assertThat(NotificationStateMachine.INITIAL_STATUSES).containsExactlyInAnyOrder(PENDING, SUPPRESSED);
        assertThatThrownBy(() -> NotificationStateMachine.INITIAL_STATUSES.add(SENT))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void everyStatusCanBeReachedFromTheInitialStatuses() {
        Set<NotificationStatus> reached = EnumSet.noneOf(NotificationStatus.class);
        Deque<NotificationStatus> toVisit = new ArrayDeque<>(NotificationStateMachine.INITIAL_STATUSES);
        while (!toVisit.isEmpty()) {
            NotificationStatus status = toVisit.removeFirst();
            if (reached.add(status)) {
                for (NotificationTrigger trigger : NotificationTrigger.values()) {
                    if (NotificationStateMachine.canApply(status, trigger)) {
                        toVisit.add(NotificationStateMachine.apply(status, trigger));
                    }
                }
            }
        }

        assertThat(reached).containsExactlyInAnyOrder(NotificationStatus.values());
    }

    @Test
    void aNotificationThatFailsTwiceAndIsRequeuedWalksTheWholeLifeCycle() {
        NotificationStatus status = PENDING;
        status = NotificationStateMachine.apply(status, CLAIM);
        assertThat(status).isEqualTo(IN_FLIGHT);
        status = NotificationStateMachine.apply(status, RETRY);
        assertThat(status).isEqualTo(RETRY_SCHEDULED);
        status = NotificationStateMachine.apply(status, CLAIM);
        status = NotificationStateMachine.apply(status, GIVE_UP);
        assertThat(status).isEqualTo(DEAD);
        status = NotificationStateMachine.apply(status, REQUEUE);
        assertThat(status).isEqualTo(PENDING);
        status = NotificationStateMachine.apply(status, CLAIM);
        status = NotificationStateMachine.apply(status, COMPLETE);
        assertThat(status).isEqualTo(SENT);
    }

    @Test
    void theArgumentsAreRequired() {
        assertThatThrownBy(() -> NotificationStateMachine.canApply(null, CLAIM))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("from");
        assertThatThrownBy(() -> NotificationStateMachine.canApply(PENDING, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("trigger");
        assertThatThrownBy(() -> NotificationStateMachine.apply(null, CLAIM))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void theAllowedStatusesOfATriggerCannotBeChanged() {
        Set<NotificationStatus> allowed = CLAIM.allowedFrom();

        assertThatThrownBy(() -> allowed.add(SENT)).isInstanceOf(UnsupportedOperationException.class);
    }
}
