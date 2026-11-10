package com.example.alerting.event;

/**
 * The outcome of submitting an event.
 *
 * @param eventId the id of the stored event; for a duplicate, the id of the event stored earlier
 * @param status the processing status of that event at the time of the answer
 * @param duplicate {@code true} if the same source and external id had been accepted before, so nothing
 *        new was stored
 */
public record IngestResult(long eventId, EventProcessingStatus status, boolean duplicate) {
}
