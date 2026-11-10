package com.example.alerting.identity;

import com.example.alerting.shared.DomainException;
import com.example.alerting.shared.ErrorCodes;
import java.util.Objects;

/**
 * The role rule: what each role may do.
 *
 * <p>An {@link Role#ADMIN} may do everything a {@link Role#USER} may; a user may not do admin things.
 * The rule lives in one place so it can be tested exhaustively and changed without touching endpoints.
 */
public final class RoleGuard {

    private RoleGuard() {
    }

    /**
     * Tells whether a role satisfies a requirement.
     *
     * @param actual the caller's role
     * @param required the role the operation needs
     * @return {@code true} if the caller may proceed
     */
    public static boolean allows(Role actual, Role required) {
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(required, "required");
        return actual == Role.ADMIN || actual == required;
    }

    /**
     * Requires that the caller has at least the given role.
     *
     * @param user the caller
     * @param required the role the operation needs
     * @throws DomainException of kind {@code FORBIDDEN} if the caller's role is too weak
     */
    public static void require(CurrentUser user, Role required) {
        if (!allows(user.role(), required)) {
            throw new DomainException(DomainException.Kind.FORBIDDEN, ErrorCodes.FORBIDDEN,
                    "This operation requires the " + required + " role.");
        }
    }
}
