package com.example.alerting.notification;

/**
 * How one delivery attempt ended. Stored as text in the {@code delivery_attempts.outcome} column.
 */
public enum AttemptOutcome {

    /** The channel accepted the message. */
    SUCCESS,

    /** The attempt failed in a way that may succeed later (timeout, server error, rate limit). */
    RETRYABLE_FAILURE,

    /** The attempt failed in a way retrying cannot fix (invalid recipient, revoked webhook). */
    PERMANENT_FAILURE,

    /** The worker never reported back before its lease expired, so the result is unknown. */
    LEASE_EXPIRED
}
