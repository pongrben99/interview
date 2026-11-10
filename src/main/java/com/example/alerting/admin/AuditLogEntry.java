package com.example.alerting.admin;

import com.example.alerting.shared.Guards;
import com.example.alerting.shared.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * One administrative action, recorded for accountability: who did what to which object, and when.
 *
 * <p>The audit log is append-only and is the system of record for admin actions (the application
 * log is not).
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

    /** Longest allowed details text. */
    public static final int DETAILS_MAX = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_log_seq")
    @SequenceGenerator(name = "audit_log_seq", sequenceName = "audit_log_seq", allocationSize = 50)
    private Long id;

    /** The admin who acted. */
    @Column(name = "actor_user_id", nullable = false)
    private long actorUserId;

    /** What was done, for example {@code REQUEUE_NOTIFICATION} or {@code PAUSE_CHANNEL}. */
    @Column(name = "action", nullable = false, length = 60)
    private String action;

    /** The kind of object acted on, for example {@code NOTIFICATION}. */
    @Column(name = "target_type", nullable = false, length = 40)
    private String targetType;

    /** The identifier of the object acted on. */
    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    /** Optional extra facts as JSON text. */
    @Column(name = "details", length = DETAILS_MAX)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected AuditLogEntry() {
    }

    /**
     * Records an admin action.
     *
     * @param actorUserId id of the admin who acted
     * @param action what was done, not blank
     * @param targetType the kind of object acted on, not blank
     * @param targetId the identifier of that object, not blank
     * @param details optional extra facts as JSON text, at most {@value #DETAILS_MAX} characters; blank becomes {@code null}
     * @param createdAt when the action happened
     * @throws IllegalArgumentException if a required text is blank or the details are too long
     */
    public AuditLogEntry(long actorUserId, String action, String targetType, String targetId, String details,
                         Instant createdAt) {
        this.actorUserId = actorUserId;
        this.action = Guards.requireNonBlank(action, "action");
        this.targetType = Guards.requireNonBlank(targetType, "targetType");
        this.targetId = Guards.requireNonBlank(targetId, "targetId");
        String normalizedDetails = Text.blankToNull(details);
        if (normalizedDetails != null && normalizedDetails.length() > DETAILS_MAX) {
            throw new IllegalArgumentException("details must not exceed " + DETAILS_MAX + " characters");
        }
        this.details = normalizedDetails;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Long getId() {
        return id;
    }

    public long getActorUserId() {
        return actorUserId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
