package com.example.alerting.event;

/**
 * Where an event stands in the matching step. Stored as text in the {@code events.processing_status}
 * column.
 *
 * <p>An accepted event starts as {@link #RECEIVED}. Matching moves it to {@link #MATCHED}, or to
 * {@link #SKIPPED_STALE} when it is too old to notify anyone, or to {@link #FAILED} after repeated
 * matching errors.
 */
public enum EventProcessingStatus {

    /** Stored, not matched against rules yet. */
    RECEIVED,

    /** Matched against all candidate rules; its notifications (if any) exist. */
    MATCHED,

    /** Older than the freshness window when matching ran, so it was stored but never matched. */
    SKIPPED_STALE,

    /** Matching failed repeatedly; an admin needs to look at it. */
    FAILED
}
