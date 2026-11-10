package com.example.alerting.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Tests for the creation rules of {@link AuditLogEntry}. */
class AuditLogEntryTest {

    private static final Instant NOW = Instant.parse("2026-11-10T13:00:00Z");

    @Test
    void anEntryKeepsAllGivenFields() {
        AuditLogEntry entry = new AuditLogEntry(9L, "REQUEUE_NOTIFICATION", "NOTIFICATION", "42",
                "{\"reason\":\"fixed webhook\"}", NOW);

        assertThat(entry.getId()).isNull();
        assertThat(entry.getActorUserId()).isEqualTo(9L);
        assertThat(entry.getAction()).isEqualTo("REQUEUE_NOTIFICATION");
        assertThat(entry.getTargetType()).isEqualTo("NOTIFICATION");
        assertThat(entry.getTargetId()).isEqualTo("42");
        assertThat(entry.getDetails()).isEqualTo("{\"reason\":\"fixed webhook\"}");
        assertThat(entry.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void blankDetailsBecomeNull() {
        assertThat(new AuditLogEntry(1L, "A", "T", "1", "  ", NOW).getDetails()).isNull();
        assertThat(new AuditLogEntry(1L, "A", "T", "1", null, NOW).getDetails()).isNull();
    }

    @Test
    void detailsOfExactlyTheMaximumLengthAreAccepted() {
        String details = "d".repeat(AuditLogEntry.DETAILS_MAX);

        assertThat(new AuditLogEntry(1L, "A", "T", "1", details, NOW).getDetails()).hasSize(AuditLogEntry.DETAILS_MAX);
    }

    @Test
    void detailsLongerThanTheMaximumAreRejected() {
        String details = "d".repeat(AuditLogEntry.DETAILS_MAX + 1);

        assertThatThrownBy(() -> new AuditLogEntry(1L, "A", "T", "1", details, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("details must not exceed 2000 characters");
    }

    @Test
    void blankRequiredTextIsRejected() {
        assertThatThrownBy(() -> new AuditLogEntry(1L, " ", "T", "1", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("action must not be blank");
        assertThatThrownBy(() -> new AuditLogEntry(1L, "A", "", "1", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("targetType must not be blank");
        assertThatThrownBy(() -> new AuditLogEntry(1L, "A", "T", null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("targetId must not be blank");
    }

    @Test
    void theCreationTimeIsRequired() {
        assertThatThrownBy(() -> new AuditLogEntry(1L, "A", "T", "1", null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("createdAt");
    }
}
