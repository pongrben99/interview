package com.example.alerting.user;

import com.example.alerting.identity.Role;
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
import java.util.Locale;
import java.util.Objects;

/**
 * A person who uses the service: defines alert rules, owns destinations and receives notifications.
 *
 * <p>The email address is the identity and is always stored in lower case. A deactivated user
 * keeps their data but receives nothing.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "users_seq")
    @SequenceGenerator(name = "users_seq", sequenceName = "users_seq", allocationSize = 50)
    private Long id;

    /** Unique, lower-case email address. */
    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private Role role;

    /** Inactive users receive no notifications. */
    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected User() {
    }

    /**
     * Creates an active user.
     *
     * @param email the email address, not blank; it is trimmed and lower-cased
     * @param displayName the name shown in the user interface, not blank
     * @param role the user's role
     * @param now the current time, used for both timestamps
     * @throws IllegalArgumentException if the email or display name is blank
     */
    public User(String email, String displayName, Role role, Instant now) {
        this.email = Guards.requireNonBlank(email, "email").toLowerCase(Locale.ROOT);
        this.displayName = Guards.requireNonBlank(displayName, "displayName");
        this.role = Objects.requireNonNull(role, "role");
        this.active = true;
        Objects.requireNonNull(now, "now");
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
