package com.example.alerting.destination;

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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;

/**
 * A user's saved contact point on one channel: an email address or a Slack webhook.
 *
 * <p>Rules refer to destinations by id, which is why a rule's {@code destinationIds} are the ids of
 * these rows and not channel ids. A user can have several destinations on the same channel.
 *
 * <p>The channel-specific settings are JSON text in {@link #getConfig()}; fields the channel marks
 * as secret (a Slack webhook URL) must never be logged or returned unmasked. The
 * {@link #getDestinationKey() destination key} is the canonical identity computed by the channel
 * adapter; it keeps a user from saving the same address twice. Destinations are soft-deleted so
 * notification history stays intact.
 */
@Entity
@Table(name = "channel_destinations")
public class ChannelDestination {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "channel_destinations_seq")
    @SequenceGenerator(name = "channel_destinations_seq", sequenceName = "channel_destinations_seq", allocationSize = 50)
    private Long id;

    /** The owner. */
    @Column(name = "user_id", nullable = false)
    private long userId;

    /** The channel's type code, for example {@code EMAIL} or {@code SLACK}. */
    @Column(name = "channel_type", nullable = false, length = 30)
    private String channelType;

    /** A name the user recognizes, for example "ops-alerts". */
    @Column(name = "label", nullable = false, length = 100)
    private String label;

    /** Channel-specific settings as JSON text, for example the email address or the webhook URL. */
    @Column(name = "config", nullable = false, length = 2000)
    private String config;

    /** Canonical identity from the channel adapter; unique per user and channel. */
    @Column(name = "destination_key", nullable = false, length = 255)
    private String destinationKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private DestinationStatus status;

    /** When the user deleted the destination, or {@code null} while it is live. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic lock: a concurrent edit fails instead of overwriting. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Required by JPA. */
    protected ChannelDestination() {
    }

    /**
     * Creates an active destination.
     *
     * @param userId id of the owning user
     * @param channelType the channel's type code, not blank
     * @param label a name the user recognizes, not blank
     * @param config the channel-specific settings as JSON text, not blank
     * @param destinationKey the canonical identity computed by the channel adapter, not blank
     * @param now the current time, used for both timestamps
     * @throws IllegalArgumentException if a required text is blank
     */
    public ChannelDestination(long userId, String channelType, String label, String config,
                              String destinationKey, Instant now) {
        this.userId = userId;
        this.channelType = Guards.requireNonBlank(channelType, "channelType");
        this.label = Guards.requireNonBlank(label, "label");
        this.config = Guards.requireNonBlank(config, "config");
        this.destinationKey = Guards.requireNonBlank(destinationKey, "destinationKey");
        this.status = DestinationStatus.ACTIVE;
        Objects.requireNonNull(now, "now");
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public String getChannelType() {
        return channelType;
    }

    public String getLabel() {
        return label;
    }

    public String getConfig() {
        return config;
    }

    public String getDestinationKey() {
        return destinationKey;
    }

    public DestinationStatus getStatus() {
        return status;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
