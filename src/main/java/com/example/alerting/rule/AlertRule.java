package com.example.alerting.rule;

import com.example.alerting.event.Severity;
import com.example.alerting.shared.Guards;
import com.example.alerting.shared.Text;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * What a user wants to be told about: a saved question of the form "tell me when an event like
 * this happens".
 *
 * <p>A rule has exactly one category and a minimum {@link Severity severity}. Finer filters
 * (regions, keywords, attribute conditions) live in {@link #getCriteria() criteria} as JSON text
 * and are evaluated by the matching module. A rule with a cooldown fires at most once per cooldown
 * period. The destinations are referred to by id; they must be the owner's own live destinations,
 * which the rule service checks. Rules are soft-deleted so notification history stays intact.
 */
@Entity
@Table(name = "alert_rules")
public class AlertRule {

    /** Longest allowed cooldown: one day. */
    public static final int MAX_COOLDOWN_SECONDS = 86_400;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "alert_rules_seq")
    @SequenceGenerator(name = "alert_rules_seq", sequenceName = "alert_rules_seq", allocationSize = 50)
    private Long id;

    /** The owner. */
    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** The {@code EventCategory} code the rule listens to. */
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    /** Events below this severity are ignored. Stored as SMALLINT. */
    @Column(name = "min_severity", nullable = false)
    private short minSeverity;

    /** Regions, keywords and attribute conditions as JSON text; {@code {}} means no extra filter. */
    @Column(name = "criteria", nullable = false, length = 4000)
    private String criteria;

    /** Minimum time between two alerts from this rule, in seconds; 0 switches the cooldown off. */
    @Column(name = "cooldown_seconds", nullable = false)
    private int cooldownSeconds;

    /** Disabled rules take no part in matching. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** When the rule last fired; the anchor of the cooldown. {@code null} if it never did. */
    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    /** When the user deleted the rule, or {@code null} while it is live. */
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

    /** Ids of the destinations that receive the rule's notifications. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "alert_rule_destinations", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "destination_id", nullable = false)
    private Set<Long> destinationIds = new HashSet<>();

    /** Required by JPA. */
    protected AlertRule() {
    }

    /**
     * Creates an enabled rule.
     *
     * @param userId id of the owning user
     * @param name a name the user recognizes, not blank
     * @param description an optional explanation; blank becomes {@code null}
     * @param category the {@code EventCategory} code, not blank
     * @param minSeverity the lowest severity that triggers the rule, 1 to 5
     * @param criteria regions, keywords and conditions as JSON text; {@code null} or blank becomes {@code {}}
     * @param cooldownSeconds minimum seconds between alerts, 0 to {@value #MAX_COOLDOWN_SECONDS}
     * @param destinationIds ids of the destinations to notify, at least one
     * @param now the current time, used for both timestamps
     * @throws IllegalArgumentException if a required text is blank, a number is out of range or
     *         there is no destination
     * @throws NullPointerException if the destination set or the time is null
     */
    public AlertRule(long userId, String name, String description, String category, int minSeverity,
                     String criteria, int cooldownSeconds, Set<Long> destinationIds, Instant now) {
        this.userId = userId;
        this.name = Guards.requireNonBlank(name, "name");
        this.description = Text.blankToNull(description);
        this.category = Guards.requireNonBlank(category, "category");
        this.minSeverity = (short) Guards.requireInRange(minSeverity, Severity.MIN_LEVEL, Severity.MAX_LEVEL, "minSeverity");
        this.criteria = criteria == null || criteria.isBlank() ? "{}" : criteria;
        this.cooldownSeconds = Guards.requireInRange(cooldownSeconds, 0, MAX_COOLDOWN_SECONDS, "cooldownSeconds");
        Set<Long> copy = Set.copyOf(destinationIds);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("destinationIds must contain at least one destination");
        }
        this.destinationIds = new HashSet<>(copy);
        this.enabled = true;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public int getMinSeverity() {
        return minSeverity;
    }

    public String getCriteria() {
        return criteria;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getLastTriggeredAt() {
        return lastTriggeredAt;
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

    /**
     * The destinations that receive this rule's notifications.
     *
     * @return an unmodifiable copy of the destination ids
     */
    public Set<Long> getDestinationIds() {
        return Set.copyOf(destinationIds);
    }
}
