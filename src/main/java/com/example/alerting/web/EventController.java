package com.example.alerting.web;

import com.example.alerting.event.EventIngestionService;
import com.example.alerting.event.IngestResult;
import com.example.alerting.identity.RequiresRole;
import com.example.alerting.identity.Role;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The push API for events: the generic entry point used by the admin console, by tests and by any
 * external system (docs/02, section 6.4).
 *
 * <p>It is a thin adapter: the work happens in {@link EventIngestionService}, which every ingestion path
 * shares. Publishing is an admin operation.
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventIngestionService ingestion;

    /**
     * Creates the controller.
     *
     * @param ingestion the shared ingestion use case
     */
    public EventController(EventIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    /**
     * Publishes an event. Publishing the same event twice is harmless.
     *
     * @param request the event
     * @return {@code 201} with the new event's id, or {@code 200} with {@code duplicate: true} if the same
     *         source and external id were accepted before
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequiresRole(Role.ADMIN)
    public ResponseEntity<PublishEventResponse> publish(@RequestBody PublishEventRequest request) {
        IngestResult result = ingestion.ingest(request.toCommand());
        return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(PublishEventResponse.from(result));
    }
}
