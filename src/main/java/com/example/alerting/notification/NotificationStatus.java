package com.example.alerting.notification;

/**
 * Where a notification stands on its way to the user. Stored as text in the
 * {@code notifications.status} column.
 *
 * <p>Which status may follow which is decided by {@link NotificationStateMachine}.
 */
public enum NotificationStatus {

    /** Created by matching and waiting to be picked up by the dispatcher. */
    PENDING,

    /** Claimed by a worker that holds a lease and is delivering it right now. */
    IN_FLIGHT,

    /** The last attempt failed in a way worth retrying; the next attempt is scheduled. */
    RETRY_SCHEDULED,

    /** Delivered. Final. */
    SENT,

    /** Will not be retried automatically (permanent failure or no attempts left). Only an admin can requeue it. */
    DEAD,

    /** Withdrawn before delivery (rule or destination disabled, user deactivated, expired, admin cancel). Final. */
    CANCELLED,

    /** Matched but deliberately not sent, for example because the rule is in cooldown. Final. */
    SUPPRESSED
}
