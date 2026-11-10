package com.example.alerting.event;

import com.example.alerting.shared.Guards;
import com.example.alerting.shared.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Something that happened in the world: an earthquake, a market move, a news item.
 *
 * <p>An event is an immutable, normalized fact. The business fields are set once in the
 * constructor and never change; only the processing fields (status, match counters) move, and
 * only through methods added with the matching step. An event is unique per source and external
 * id, which makes publishing the same event twice harmless.
 *
 * <p>The foreign keys are kept as plain ids so that modules do not depend on each other's
 * entities.
 */
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "events_seq")
    @SequenceGenerator(name = "events_seq", sequenceName = "events_seq", allocationSize = 50)
    private Long id;

    /** The {@link EventSource} that produced the event. */
    @Column(name = "source_id", nullable = false)
    private long sourceId;

    /** The source's own identifier for the event; with the source it identifies the event. */
    @Column(name = "external_id", nullable = false, length = 200)
    private String externalId;

    /** The {@link EventCategory#getCode() category code}. */
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    /** Importance from 1 to 5, see {@link Severity}. Stored as SMALLINT. */
    @Column(name = "severity", nullable = false)
    private short severity;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "summary", length = 2000)
    private String summary;

    /** A link to more information, if the source provides one. */
    @Column(name = "url", length = 2000)
    private String url;

    /** Upper-case region code such as {@code JP} or {@code US-CA}, or {@code null} if unknown. */
    @Column(name = "region", length = 50)
    private String region;

    /** When the event happened, according to the source. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** When this service accepted the event. */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /** Category-specific facts as a JSON object of scalar values, for example {@code {"magnitude": 6.4}}. */
    @Column(name = "attributes", nullable = false, length = 8000)
    private String attributes;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 16)
    private EventProcessingStatus processingStatus;

    /** When matching finished, or {@code null} while it has not. */
    @Column(name = "matched_at")
    private Instant matchedAt;

    /** How many notifications matching created, including suppressed ones. */
    @Column(name = "match_count", nullable = false)
    private int matchCount;

    /** How many times matching was tried and failed. */
    @Column(name = "match_attempts", nullable = false)
    private int matchAttempts;

    /** Message of the last matching failure, or {@code null}. */
    @Column(name = "match_error", length = 500)
    private String matchError;

    /** Required by JPA. */
    protected Event() {
    }

    /**
     * Creates a newly received event, ready to be matched.
     *
     * @param sourceId id of the source that produced the event
     * @param externalId the source's identifier for the event, not blank
     * @param category the category code, not blank
     * @param severity importance from {@link Severity#MIN_LEVEL} to {@link Severity#MAX_LEVEL}
     * @param title a short headline, not blank
     * @param summary an optional longer text; blank becomes {@code null}
     * @param url an optional link; blank becomes {@code null}
     * @param region an optional region code; it is trimmed and upper-cased, blank becomes {@code null}
     * @param occurredAt when the event happened
     * @param receivedAt when this service accepted it
     * @param attributes category-specific facts as JSON text; {@code null} or blank becomes {@code {}}
     * @throws IllegalArgumentException if a required text is blank or the severity is out of range
     */
    public Event(long sourceId, String externalId, String category, int severity, String title,
                 String summary, String url, String region, Instant occurredAt, Instant receivedAt,
                 String attributes) {
        this.sourceId = sourceId;
        this.externalId = Guards.requireNonBlank(externalId, "externalId");
        this.category = Guards.requireNonBlank(category, "category");
        this.severity = (short) Guards.requireInRange(severity, Severity.MIN_LEVEL, Severity.MAX_LEVEL, "severity");
        this.title = Guards.requireNonBlank(title, "title");
        this.summary = Text.blankToNull(summary);
        this.url = Text.blankToNull(url);
        String normalizedRegion = Text.blankToNull(region);
        this.region = normalizedRegion == null ? null : normalizedRegion.toUpperCase(Locale.ROOT);
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        this.attributes = attributes == null || attributes.isBlank() ? "{}" : attributes;
        this.processingStatus = EventProcessingStatus.RECEIVED;
    }

    public Long getId() {
        return id;
    }

    public long getSourceId() {
        return sourceId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCategory() {
        return category;
    }

    public int getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getUrl() {
        return url;
    }

    public String getRegion() {
        return region;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getAttributes() {
        return attributes;
    }

    public EventProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public Instant getMatchedAt() {
        return matchedAt;
    }

    public int getMatchCount() {
        return matchCount;
    }

    public int getMatchAttempts() {
        return matchAttempts;
    }

    public String getMatchError() {
        return matchError;
    }
}
