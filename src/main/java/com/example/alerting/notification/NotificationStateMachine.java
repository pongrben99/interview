package com.example.alerting.notification;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The rules of the notification life cycle, as a pure function with no framework dependencies.
 *
 * <p>Every change of a notification's status goes through {@link #apply}. The legal moves are the
 * ones listed on {@link NotificationTrigger}; everything else is rejected. The database adds a second
 * line of defence: each conditional update names the status it expects, so two workers cannot both
 * move the same notification.
 */
public final class NotificationStateMachine {

    /** The statuses a notification may be created in: {@code PENDING} by matching, {@code SUPPRESSED} by cooldown. */
    public static final Set<NotificationStatus> INITIAL_STATUSES =
            Collections.unmodifiableSet(EnumSet.of(NotificationStatus.PENDING, NotificationStatus.SUPPRESSED));

    private NotificationStateMachine() {
    }

    /**
     * Tells whether a trigger may be applied in a status.
     *
     * @param from the notification's current status
     * @param trigger the trigger to apply
     * @return {@code true} if the move is legal
     */
    public static boolean canApply(NotificationStatus from, NotificationTrigger trigger) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(trigger, "trigger");
        return trigger.allowedFrom().contains(from);
    }

    /**
     * Applies a trigger and returns the resulting status.
     *
     * @param from the notification's current status
     * @param trigger the trigger to apply
     * @return the new status
     * @throws IllegalStateTransitionException if the trigger is not allowed in {@code from}
     */
    public static NotificationStatus apply(NotificationStatus from, NotificationTrigger trigger) {
        if (!canApply(from, trigger)) {
            throw new IllegalStateTransitionException(from, trigger);
        }
        return trigger.target();
    }
}
