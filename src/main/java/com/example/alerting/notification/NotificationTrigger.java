package com.example.alerting.notification;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Something that happens to a notification and may change its status.
 *
 * <p>Each trigger knows the one status it leads to and the statuses it may be applied in. Together
 * the constants are the complete transition table of the notification life cycle (docs/02, section
 * 8.1):
 *
 * <pre>
 * trigger         allowed in                  leads to
 * CLAIM           PENDING, RETRY_SCHEDULED    IN_FLIGHT
 * COMPLETE        IN_FLIGHT                   SENT
 * RETRY           IN_FLIGHT                   RETRY_SCHEDULED
 * GIVE_UP         IN_FLIGHT                   DEAD
 * CANCEL_CLAIMED  IN_FLIGHT                   CANCELLED
 * CANCEL_QUEUED   PENDING, RETRY_SCHEDULED    CANCELLED
 * REQUEUE         DEAD                        PENDING
 * </pre>
 *
 * <p>A notification is created directly as {@link NotificationStatus#PENDING} or
 * {@link NotificationStatus#SUPPRESSED}; creation is not a trigger.
 */
public enum NotificationTrigger {

    /** A worker claims a queued notification and starts delivering it. */
    CLAIM(NotificationStatus.IN_FLIGHT, NotificationStatus.PENDING, NotificationStatus.RETRY_SCHEDULED),

    /** The channel accepted the message. */
    COMPLETE(NotificationStatus.SENT, NotificationStatus.IN_FLIGHT),

    /** Delivery failed for a reason worth retrying, or the worker's lease expired, and attempts remain. */
    RETRY(NotificationStatus.RETRY_SCHEDULED, NotificationStatus.IN_FLIGHT),

    /** Delivery failed for good, or no attempts remain. */
    GIVE_UP(NotificationStatus.DEAD, NotificationStatus.IN_FLIGHT),

    /**
     * The dispatcher's guard cancels a notification it has just claimed (inactive destination,
     * deactivated user, expired). Running after the claim keeps the check free of races.
     */
    CANCEL_CLAIMED(NotificationStatus.CANCELLED, NotificationStatus.IN_FLIGHT),

    /**
     * A queued notification is withdrawn: its rule or destination was disabled, its user was
     * deactivated, or an admin cancelled it. A notification being delivered cannot be cancelled this way.
     */
    CANCEL_QUEUED(NotificationStatus.CANCELLED, NotificationStatus.PENDING, NotificationStatus.RETRY_SCHEDULED),

    /** An admin puts a dead notification back in the queue. */
    REQUEUE(NotificationStatus.PENDING, NotificationStatus.DEAD);

    private final NotificationStatus target;
    private final Set<NotificationStatus> allowedFrom;

    NotificationTrigger(NotificationStatus target, NotificationStatus first, NotificationStatus... others) {
        this.target = target;
        this.allowedFrom = Collections.unmodifiableSet(EnumSet.of(first, others));
    }

    /**
     * The status a notification has after this trigger was applied.
     *
     * @return the target status
     */
    public NotificationStatus target() {
        return target;
    }

    /**
     * The statuses this trigger may be applied in.
     *
     * @return an unmodifiable, non-empty set
     */
    public Set<NotificationStatus> allowedFrom() {
        return allowedFrom;
    }
}
