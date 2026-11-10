package com.example.alerting.destination;

/**
 * Whether a destination may receive notifications. Stored as text in the
 * {@code channel_destinations.status} column.
 */
public enum DestinationStatus {

    /** Notifications are delivered to this destination. */
    ACTIVE,

    /** Switched off: pending notifications are cancelled and no new ones are created. */
    DISABLED
}
