package com.example.alerting.identity;

import java.util.Optional;

/**
 * Looks up the people who may use the service.
 *
 * <p>The {@code user} module implements this port on top of its user table. The identity module only
 * defines it, so it never depends on the module that owns the users.
 */
public interface IdentityDirectory {

    /**
     * Finds an <em>active</em> user by email address.
     *
     * @param email the lower-case email address
     * @return the user, or empty if there is no such user or the user has been deactivated
     */
    Optional<CurrentUser> findActiveByEmail(String email);
}
