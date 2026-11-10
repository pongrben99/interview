/**
 * Events and how they get in. Holds the event, category and source entities, the idempotent
 * ingestion use case, the per-category severity normalizers, the event simulator and the SPI for
 * poll connectors. An event is an immutable, normalized fact with a severity from 1 to 5
 * (docs/02, section 6).
 *
 * <p>Dependency rule: depends on {@code shared} only.
 */
package com.example.alerting.event;
