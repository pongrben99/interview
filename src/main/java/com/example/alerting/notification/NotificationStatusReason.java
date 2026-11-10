package com.example.alerting.notification;

/**
 * Why a notification ended up in its status, where that is not obvious. Stored as text in the
 * {@code notifications.status_reason} column; {@code null} when no reason applies.
 */
public enum NotificationStatusReason {

    /** {@link NotificationStatus#SUPPRESSED}: the rule had fired too recently. */
    COOLDOWN,

    /** {@link NotificationStatus#CANCELLED}: the rule was disabled or deleted. */
    RULE_DISABLED,

    /** {@link NotificationStatus#CANCELLED}: the destination was disabled or deleted. */
    DESTINATION_DISABLED,

    /** {@link NotificationStatus#CANCELLED}: the user was deactivated. */
    USER_INACTIVE,

    /** {@link NotificationStatus#CANCELLED}: an admin cancelled it. */
    ADMIN_CANCEL,

    /** {@link NotificationStatus#CANCELLED}: it waited longer than the maximum age and was never sent. */
    EXPIRED,

    /** {@link NotificationStatus#DEAD}: its channel type is unknown to the registry. */
    UNKNOWN_CHANNEL,

    /** {@link NotificationStatus#DEAD}: the channel reported a failure that retrying cannot fix. */
    PERMANENT_FAILURE,

    /** {@link NotificationStatus#DEAD}: every allowed attempt failed. */
    ATTEMPTS_EXHAUSTED
}
