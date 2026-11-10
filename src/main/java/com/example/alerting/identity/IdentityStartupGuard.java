package com.example.alerting.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Stops the application from starting with the demo identity outside the {@code local} profile.
 *
 * <p>The header stub trusts whatever the caller claims, so running it anywhere real would be a security
 * hole. This guard applies {@link IdentityStubPolicy} while the application starts: in another profile
 * the context fails to start with an explanation, unless the loudly named override
 * {@code alerting.identity.allow-insecure-stub-outside-local} is set, in which case it starts and logs a
 * warning.
 */
@Component
public class IdentityStartupGuard implements InitializingBean {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityStartupGuard.class);

    private final Environment environment;
    private final IdentityProperties properties;

    /**
     * Creates the guard.
     *
     * @param environment tells which profiles are active; the default profile counts when none is chosen
     * @param properties the identity settings, including the override
     */
    public IdentityStartupGuard(Environment environment, IdentityProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    /**
     * Applies the policy.
     *
     * @throws IllegalStateException if the stub is not allowed here, which aborts the application start
     */
    @Override
    public void afterPropertiesSet() {
        boolean local = environment.acceptsProfiles(Profiles.of(IdentityStubPolicy.LOCAL_PROFILE));
        switch (IdentityStubPolicy.decide(local, properties.allowInsecureStubOutsideLocal())) {
            case ALLOWED_IN_LOCAL -> LOG.debug("Demo identity (X-User-Id header) enabled for the local profile");
            case ALLOWED_BY_OVERRIDE -> LOG.warn("SECURITY WARNING: the demo identity (X-User-Id header) is running "
                    + "outside the '{}' profile because alerting.identity.allow-insecure-stub-outside-local is set. "
                    + "Anyone who can reach this service can act as any user.", IdentityStubPolicy.LOCAL_PROFILE);
            case REFUSED -> throw new IllegalStateException("Refusing to start: the demo identity (X-User-Id header) "
                    + "is for the '" + IdentityStubPolicy.LOCAL_PROFILE + "' profile only, because anyone who can "
                    + "reach the service could act as any user. Activate that profile, or put real authentication in "
                    + "front of the service. To start anyway (not recommended) set "
                    + "alerting.identity.allow-insecure-stub-outside-local=true.");
        }
    }
}
