package com.example.alerting.notification;

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
 * One intended delivery of one event to one destination because of one rule. This is the outbox row:
 * it is created in the same transaction that marks its event matched, so an alert is either fully
 * recorded or not at all.
 *
 * <p>The combination of event, rule and destination is unique, which makes matching idempotent. The
 * status moves only along the transitions of {@link NotificationStateMachine}. The delivery
 * bookkeeping (attempt counter, lease, error) is updated by the dispatcher with conditional database
 * updates; the history of every try lives in {@link DeliveryAttempt}.
 *
 * <p>References to other modules are kept as plain ids so that modules do not depend on each
 * other's entities.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notifications_seq")
    @SequenceGenerator(name = "notifications_seq", sequenceName = "notifications_seq", allocationSize = 50)
    private Long id;

    /** The event that triggered the notification. */
    @Column(name = "event_id", nullable = false)
    private long eventId;

    /** The rule that matched the event. */
    @Column(name = "alert_rule_id", nullable = false)
    private long alertRuleId;

    /** The destination the message goes to. */
    @Column(name = "destination_id", nullable = false)
    private long destinationId;

    /** The recipient, copied from the rule's owner so history queries and guards need no join. */
    @Column(name = "user_id", nullable = false)
    private long userId;

    /** Channel type code, copied from the destination at match time so routing needs no join. */
    @Column(name = "channel_type", nullable = false, length = 30)
    private String channelType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationStatus status;

    /** Why the notification has its status, where that is not obvious; {@code null} otherwise. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_reason", length = 60)
    private NotificationStatusReason statusReason;

    /** Attempts started so far; a worker increments it when it claims the notification. */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /** Attempts allowed in total. An admin requeue raises it. */
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    /** Earliest time the next attempt may start. */
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    /** Id of the worker that holds the lease, or {@code null}. */
    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    /** When the lease expires, or {@code null}. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_error_code", length = 50)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** When the message was delivered, or {@code null}. */
    @Column(name = "sent_at")
    private Instant sentAt;

    /** Optimistic lock for admin operations. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Required by JPA. */
    protected Notification() {
    }

    private Notification(long eventId, long alertRuleId, long destinationId, long userId, String channelType,
                         NotificationStatus status, NotificationStatusReason statusReason, int maxAttempts,
                         Instant now) {
        this.eventId = eventId;
        this.alertRuleId = alertRuleId;
        this.destinationId = destinationId;
        this.userId = userId;
        this.channelType = Guards.requireNonBlank(channelType, "channelType");
        this.status = status;
        this.statusReason = statusReason;
        this.maxAttempts = Guards.requireAtLeast(maxAttempts, 1, "maxAttempts");
        Objects.requireNonNull(now, "now");
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Creates a notification that is waiting to be delivered, due immediately.
     *
     * @param eventId the triggering event
     * @param alertRuleId the matching rule
     * @param destinationId the destination to deliver to
     * @param userId the recipient
     * @param channelType the destination's channel type code, not blank
     * @param maxAttempts how many delivery attempts are allowed, at least 1
     * @param now the current time
     * @return a {@link NotificationStatus#PENDING} notification
     * @throws IllegalArgumentException if the channel type is blank or {@code maxAttempts} is below 1
     */
    public static Notification pending(long eventId, long alertRuleId, long destinationId, long userId,
                                       String channelType, int maxAttempts, Instant now) {
        return new Notification(eventId, alertRuleId, destinationId, userId, channelType,
                NotificationStatus.PENDING, null, maxAttempts, now);
    }

    /**
     * Creates a notification for a match that is deliberately not delivered because its rule fired
     * too recently. It is kept so users and admins can see why no alert arrived.
     *
     * @param eventId the triggering event
     * @param alertRuleId the matching rule
     * @param destinationId the destination that would have received it
     * @param userId the recipient
     * @param channelType the destination's channel type code, not blank
     * @param maxAttempts the retry budget the notification would have had, at least 1
     * @param now the current time
     * @return a {@link NotificationStatus#SUPPRESSED} notification with reason
     *         {@link NotificationStatusReason#COOLDOWN}
     * @throws IllegalArgumentException if the channel type is blank or {@code maxAttempts} is below 1
     */
    public static Notification suppressedByCooldown(long eventId, long alertRuleId, long destinationId,
                                                    long userId, String channelType, int maxAttempts,
                                                    Instant now) {
        return new Notification(eventId, alertRuleId, destinationId, userId, channelType,
                NotificationStatus.SUPPRESSED, NotificationStatusReason.COOLDOWN, maxAttempts, now);
    }

    public Long getId() {
        return id;
    }

    public long getEventId() {
        return eventId;
    }

    public long getAlertRuleId() {
        return alertRuleId;
    }

    public long getDestinationId() {
        return destinationId;
    }

    public long getUserId() {
        return userId;
    }

    public String getChannelType() {
        return channelType;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public NotificationStatusReason getStatusReason() {
        return statusReason;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public long getVersion() {
        return version;
    }
}
