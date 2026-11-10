package com.example.alerting.event;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access to {@link EventCategory} rows, keyed by category code. */
public interface EventCategoryRepository extends JpaRepository<EventCategory, String> {
}
