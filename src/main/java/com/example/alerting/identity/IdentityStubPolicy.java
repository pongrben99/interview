package com.example.alerting.identity;

/**
 * The fail-closed rule for the demo identity (docs/03, P-01): the header stub may only run in the
 * {@code local} profile, unless a separate, loudly named override is set.
 *
 * <p>The rule is a pure function so it can be tested for every combination; {@link IdentityStartupGuard}
 * applies it at start-up.
 */
public final class IdentityStubPolicy {

    /** The only profile in which the header identity stub is allowed without an override. */
    public static final String LOCAL_PROFILE = "local";

    /** What start-up should do about the stub. */
    public enum Decision {

        /** The {@code local} profile is active: fine, this is a demo. */
        ALLOWED_IN_LOCAL,

        /** Another profile is active but the override is set: start, and warn loudly. */
        ALLOWED_BY_OVERRIDE,

        /** Another profile is active and there is no override: refuse to start. */
        REFUSED
    }

    private IdentityStubPolicy() {
    }

    /**
     * Decides whether the stub may run.
     *
     * @param localProfileActive whether the {@value #LOCAL_PROFILE} profile is active
     * @param overrideSet whether the insecure-stub override is set
     * @return what start-up should do
     */
    public static Decision decide(boolean localProfileActive, boolean overrideSet) {
        if (localProfileActive) {
            return Decision.ALLOWED_IN_LOCAL;
        }
        return overrideSet ? Decision.ALLOWED_BY_OVERRIDE : Decision.REFUSED;
    }
}
