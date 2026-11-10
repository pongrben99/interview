package com.example.alerting.event;

import com.example.alerting.shared.Guards;
import com.example.alerting.shared.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A coarse class of event, such as {@code NEWS}, {@code MARKET} or {@code DISASTER}.
 *
 * <p>Categories are reference data: new ones are added as data rather than as code, and an
 * event or rule can only use a category that exists here.
 */
@Entity
@Table(name = "event_categories")
public class EventCategory {

    /** Natural key, for example {@code NEWS}. */
    @Id
    @Column(name = "code", nullable = false, length = 30)
    private String code;

    /** Human-readable name shown in the user interface. */
    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    /** Optional longer explanation. */
    @Column(name = "description", length = 300)
    private String description;

    /** Whether new events and rules may use this category. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Required by JPA. */
    protected EventCategory() {
    }

    /**
     * Creates a category.
     *
     * @param code the natural key, not blank
     * @param displayName the human-readable name, not blank
     * @param description an optional explanation; blank becomes {@code null}
     * @param enabled whether the category may be used
     */
    public EventCategory(String code, String displayName, String description, boolean enabled) {
        this.code = Guards.requireNonBlank(code, "code");
        this.displayName = Guards.requireNonBlank(displayName, "displayName");
        this.description = Text.blankToNull(description);
        this.enabled = enabled;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
