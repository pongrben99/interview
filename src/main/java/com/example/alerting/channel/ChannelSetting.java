package com.example.alerting.channel;

import com.example.alerting.shared.Guards;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Admin-controlled settings of one delivery channel.
 *
 * <p>There is one row per channel type. Rows are created by the channel registry at start-up for
 * every channel it discovers, so adding a channel needs no database migration. An admin can pause a
 * channel without redeploying: its notifications stay queued and flow again on resume.
 */
@Entity
@Table(name = "channel_settings")
public class ChannelSetting {

    /** Natural key: the channel's type code, for example {@code EMAIL} or {@code SLACK}. */
    @Id
    @Column(name = "channel_type", nullable = false, length = 30)
    private String channelType;

    /** {@code false} while an admin has paused the channel. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Overrides the default retry budget for this channel, or {@code null} to use the default. */
    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Id of the admin who last changed the row, or {@code null} if the system created it. */
    @Column(name = "updated_by")
    private Long updatedBy;

    /** Required by JPA. */
    protected ChannelSetting() {
    }

    /**
     * Creates the default settings of a newly discovered channel: enabled, default retry budget.
     *
     * @param channelType the channel's type code, not blank
     * @param now the current time
     * @throws IllegalArgumentException if the type code is blank
     */
    public ChannelSetting(String channelType, Instant now) {
        this.channelType = Guards.requireNonBlank(channelType, "channelType");
        this.enabled = true;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public String getChannelType() {
        return channelType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
