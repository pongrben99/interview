package com.example.alerting.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.alerting.identity.Role;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Tests for the creation rules of {@link User}. */
class UserTest {

    private static final Instant NOW = Instant.parse("2026-11-10T09:00:00Z");

    @Test
    void aNewUserIsActiveAndKeepsTheGivenFields() {
        User user = new User("alice@example.com", "Alice", Role.USER, NOW);

        assertThat(user.getId()).isNull();
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getDisplayName()).isEqualTo("Alice");
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isActive()).isTrue();
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void theEmailIsTrimmedAndStoredInLowerCase() {
        assertThat(new User("  Alice.Smith@Example.COM ", "Alice", Role.ADMIN, NOW).getEmail())
                .isEqualTo("alice.smith@example.com");
    }

    @Test
    void theDisplayNameIsTrimmed() {
        assertThat(new User("a@example.com", "  Alice Smith ", Role.USER, NOW).getDisplayName())
                .isEqualTo("Alice Smith");
    }

    @Test
    void aBlankEmailOrDisplayNameIsRejected() {
        assertThatThrownBy(() -> new User(" ", "Alice", Role.USER, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("email must not be blank");
        assertThatThrownBy(() -> new User("a@example.com", "", Role.USER, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("displayName must not be blank");
    }

    @Test
    void theRoleAndTheTimeAreRequired() {
        assertThatThrownBy(() -> new User("a@example.com", "Alice", null, NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("role");
        assertThatThrownBy(() -> new User("a@example.com", "Alice", Role.USER, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("now");
    }
}
