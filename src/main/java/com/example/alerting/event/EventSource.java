package com.example.alerting.event;

import com.example.alerting.shared.Guards;
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
import java.util.Objects;

/**
 * Where events come from: the push API, the simulator, or a poll connector.
 *
 * <p>Every event refers to its source, and an event is unique per source and external id. An admin
 * can disable a source; the health fields record how the last poll of a {@link SourceType#POLL}
 * source went.
 */
@Entity
@Table(name = "event_sources")
public class EventSource {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "event_sources_seq")
    @SequenceGenerator(name = "event_sources_seq", sequenceName = "event_sources_seq", allocationSize = 50)
    private Long id;

    /** Stable machine name, for example {@code manual} or {@code usgs-earthquakes}. Unique. */
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    /** Human-readable name. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** How the source delivers events. */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 12)
    private SourceType type;

    /** Disabled sources reject new events. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Source-specific settings as JSON text; {@code {}} when there are none. */
    @Column(name = "config", nullable = false, length = 2000)
    private String config;

    /** When a poll of this source last succeeded, or {@code null} if never. */
    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    /** When a poll of this source last failed, or {@code null} if never. */
    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    /** Message of the last failed poll, or {@code null}. */
    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected EventSource() {
    }

    /**
     * Creates a source that has never been polled.
     *
     * @param code the machine name, not blank
     * @param name the human-readable name, not blank
     * @param type how the source delivers events
     * @param enabled whether the source accepts events
     * @param config settings as JSON text; {@code null} or blank becomes {@code {}}
     * @param createdAt when the source was registered
     */
    public EventSource(String code, String name, SourceType type, boolean enabled, String config, Instant createdAt) {
        this.code = Guards.requireNonBlank(code, "code");
        this.name = Guards.requireNonBlank(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.enabled = enabled;
        this.config = config == null || config.isBlank() ? "{}" : config;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public SourceType getType() {
        return type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getConfig() {
        return config;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public Instant getLastErrorAt() {
        return lastErrorAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
