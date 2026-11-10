package com.example.alerting.identity;

import java.util.Optional;

/**
 * The port that turns an incoming request into a {@link CurrentUser}.
 *
 * <p>This is where real authentication plugs in later: an implementation that verifies a signed token
 * replaces {@link HeaderIdentityResolver} and nothing else changes. Implementations must return empty,
 * never throw, when the caller cannot be identified.
 */
public interface IdentityResolver {

    /**
     * Identifies the caller.
     *
     * @param headers the request headers
     * @return the caller, or empty if the request carries no valid identity
     */
    Optional<CurrentUser> resolve(RequestHeaders headers);
}
