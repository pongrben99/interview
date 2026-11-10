package com.example.alerting.event;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access to {@link EventSource} rows. */
public interface EventSourceRepository extends JpaRepository<EventSource, Long> {

    /**
     * Finds a source by its machine name.
     *
     * @param code the source code, for example {@code manual}
     * @return the source, or empty if there is none with that code
     */
    Optional<EventSource> findByCode(String code);
}
