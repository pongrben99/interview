package com.example.alerting.web;

import com.example.alerting.event.IngestResult;

/**
 * The answer to {@code POST /api/v1/events}: {@code 201} with {@code duplicate: false} for a new event,
 * {@code 200} with {@code duplicate: true} when the same source and external id were accepted before.
 *
 * @param id the id of the stored event; for a duplicate, of the event stored earlier
 * @param status the processing status of that event, for example {@code RECEIVED}
 * @param duplicate whether nothing new was stored
 */
public record PublishEventResponse(long id, String status, boolean duplicate) {

    /**
     * Builds the response for an ingestion result.
     *
     * @param result the outcome reported by the ingestion service
     * @return the response body
     */
    static PublishEventResponse from(IngestResult result) {
        return new PublishEventResponse(result.eventId(), result.status().name(), result.duplicate());
    }
}
