package com.example.alerting.notification;

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
import java.util.Objects;

/**
 * One try to deliver a notification, kept forever as the delivery ledger.
 *
 * <p>The ledger is append-only: an attempt is written once and never changed. Attempt numbers are
 * unique per notification, so a notification with {@code attempt_count} three has attempts one to
 * three. Error texts are shortened to fit their columns and must never contain secrets.
 */
@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttempt {

    private static final int ERROR_CODE_MAX = 50;
    private static final int ERROR_MESSAGE_MAX = 500;
    private static final int PROVIDER_MESSAGE_ID_MAX = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "delivery_attempts_seq")
    @SequenceGenerator(name = "delivery_attempts_seq", sequenceName = "delivery_attempts_seq", allocationSize = 50)
    private Long id;

    /** The notification this attempt belongs to. */
    @Column(name = "notification_id", nullable = false)
    private long notificationId;

    /** 1 for the first attempt, 2 for the second, and so on. */
    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 24)
    private AttemptOutcome outcome;

    /** A short machine-readable failure code, or {@code null} on success. */
    @Column(name = "error_code", length = ERROR_CODE_MAX)
    private String errorCode;

    /** A short human-readable failure message, or {@code null} on success. */
    @Column(name = "error_message", length = ERROR_MESSAGE_MAX)
    private String errorMessage;

    /** The HTTP status the channel answered with, if it has one. */
    @Column(name = "http_status")
    private Integer httpStatus;

    /** How long the attempt took, in milliseconds. */
    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    /** The provider's identifier for the sent message (email Message-ID, {@code dry-run}), if any. */
    @Column(name = "provider_message_id", length = PROVIDER_MESSAGE_ID_MAX)
    private String providerMessageId;

    /** The worker that made the attempt. */
    @Column(name = "worker_id", nullable = false, length = 100)
    private String workerId;

    /** Required by JPA. */
    protected DeliveryAttempt() {
    }

    /**
     * Records one finished attempt.
     *
     * @param notificationId the notification the attempt belongs to
     * @param attemptNo the attempt number, at least 1
     * @param startedAt when the attempt started
     * @param finishedAt when it ended, not before {@code startedAt}
     * @param outcome how it ended
     * @param errorCode an optional failure code; shortened to 50 characters
     * @param errorMessage an optional failure message; shortened to 500 characters
     * @param httpStatus the HTTP status the channel answered with, or {@code null}
     * @param latencyMs how long it took in milliseconds, not negative
     * @param providerMessageId the provider's message identifier, or {@code null}; shortened to 200 characters
     * @param workerId the worker that made the attempt, not blank
     * @throws IllegalArgumentException if a number or text is invalid, or the attempt ends before it started
     */
    public DeliveryAttempt(long notificationId, int attemptNo, Instant startedAt, Instant finishedAt,
                           AttemptOutcome outcome, String errorCode, String errorMessage, Integer httpStatus,
                           int latencyMs, String providerMessageId, String workerId) {
        this.notificationId = notificationId;
        this.attemptNo = Guards.requireAtLeast(attemptNo, 1, "attemptNo");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt must not be before startedAt");
        }
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.errorCode = Text.truncate(Text.blankToNull(errorCode), ERROR_CODE_MAX);
        this.errorMessage = Text.truncate(Text.blankToNull(errorMessage), ERROR_MESSAGE_MAX);
        this.httpStatus = httpStatus;
        this.latencyMs = Guards.requireAtLeast(latencyMs, 0, "latencyMs");
        this.providerMessageId = Text.truncate(Text.blankToNull(providerMessageId), PROVIDER_MESSAGE_ID_MAX);
        this.workerId = Guards.requireNonBlank(workerId, "workerId");
    }

    public Long getId() {
        return id;
    }

    public long getNotificationId() {
        return notificationId;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public AttemptOutcome getOutcome() {
        return outcome;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getWorkerId() {
        return workerId;
    }
}
