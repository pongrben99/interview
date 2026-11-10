package com.example.alerting.identity;

import java.util.Objects;

/**
 * The person making the current request.
 *
 * <p>This is the only thing business code ever learns about the caller. How the caller was identified
 * (today a demo header, later a verified token) stays behind {@link IdentityResolver}, so replacing the
 * authentication does not touch the business code.
 *
 * @param id the user's id
 * @param email the user's lower-case email address
 * @param displayName the name shown in the user interface
 * @param role the user's role
 */
public record CurrentUser(long id, String email, String displayName, Role role) {

    /**
     * Creates a current user.
     *
     * @throws NullPointerException if the email, the display name or the role is {@code null}
     */
    public CurrentUser {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(role, "role");
    }
}
