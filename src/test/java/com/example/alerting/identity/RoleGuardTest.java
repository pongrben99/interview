package com.example.alerting.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.alerting.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The role rule: an admin may do everything a user may; a user may not do admin things. */
class RoleGuardTest {

    private static CurrentUser user(Role role) {
        return new CurrentUser(1L, "someone@example.com", "Someone", role);
    }

    @ParameterizedTest(name = "{0} may do what needs {1}: {2}")
    @CsvSource({"USER,USER,true", "USER,ADMIN,false", "ADMIN,USER,true", "ADMIN,ADMIN,true"})
    void everyCombinationOfActualAndRequiredRole(Role actual, Role required, boolean expected) {
        assertThat(RoleGuard.allows(actual, required)).isEqualTo(expected);
    }

    @Test
    void requireLetsAnAllowedCallerThrough() {
        RoleGuard.require(user(Role.ADMIN), Role.ADMIN);
        RoleGuard.require(user(Role.ADMIN), Role.USER);
        RoleGuard.require(user(Role.USER), Role.USER);
    }

    @Test
    void requireRefusesATooWeakCallerWithAForbiddenError() {
        assertThatThrownBy(() -> RoleGuard.require(user(Role.USER), Role.ADMIN))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.getKind()).isEqualTo(DomainException.Kind.FORBIDDEN);
                    assertThat(e.getCode()).isEqualTo("FORBIDDEN");
                    assertThat(e).hasMessage("This operation requires the ADMIN role.");
                });
    }

    @Test
    void nullRolesAreProgrammingErrors() {
        assertThatThrownBy(() -> RoleGuard.allows(null, Role.USER)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RoleGuard.allows(Role.USER, null)).isInstanceOf(NullPointerException.class);
    }
}
