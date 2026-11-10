package com.example.alerting.notification;

/**
 * Thrown when a {@link NotificationTrigger} is applied to a notification in a status where it is
 * not allowed, for example cancelling a notification that is already {@link NotificationStatus#SENT}.
 *
 * <p>The admin API maps this to HTTP 409 and reports the current status.
 */
public class IllegalStateTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final NotificationStatus from;
    private final NotificationTrigger trigger;

    /**
     * Creates the exception.
     *
     * @param from the status the notification is in
     * @param trigger the trigger that cannot be applied there
     */
    public IllegalStateTransitionException(NotificationStatus from, NotificationTrigger trigger) {
        super("Cannot apply " + trigger + " to a notification in status " + from);
        this.from = from;
        this.trigger = trigger;
    }

    /**
     * The status the notification was in.
     *
     * @return the current status
     */
    public NotificationStatus getFrom() {
        return from;
    }

    /**
     * The trigger that was rejected.
     *
     * @return the rejected trigger
     */
    public NotificationTrigger getTrigger() {
        return trigger;
    }
}
