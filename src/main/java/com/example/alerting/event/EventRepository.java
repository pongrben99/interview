package com.example.alerting.event;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access to {@link Event} rows. */
public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Looks up an event by the key that makes it unique. Ingestion uses this to treat a repeated
     * submission as a duplicate instead of storing it twice.
     *
     * @param sourceId id of the source
     * @param externalId the source's identifier for the event
     * @return the event, or empty if the source never reported that external id
     */
    Optional<Event> findBySourceIdAndExternalId(long sourceId, String externalId);
}
