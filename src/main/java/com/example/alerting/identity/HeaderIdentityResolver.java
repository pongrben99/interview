package com.example.alerting.identity;

import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The demo identity: the caller names themselves with the {@value #HEADER} header, which carries the
 * email address of a seeded user.
 *
 * <p><strong>This is not authentication.</strong> Anyone who can reach the service can claim to be
 * anyone. It exists so the role and ownership rules can be built and tested for real before an identity
 * provider is chosen, and {@link IdentityStartupGuard} refuses to start the application with it outside
 * the {@code local} profile. Unknown and deactivated users are treated the same way, so the response does
 * not reveal which addresses exist.
 */
@Component
public class HeaderIdentityResolver implements IdentityResolver {

    /** The request header that carries the caller's email address. */
    public static final String HEADER = "X-User-Id";

    /** The longest valid email address; anything longer cannot be a user and never reaches the database. */
    static final int MAX_EMAIL_LENGTH = 320;

    private final IdentityDirectory directory;

    /**
     * Creates the resolver.
     *
     * @param directory where the users are looked up
     */
    public HeaderIdentityResolver(IdentityDirectory directory) {
        this.directory = directory;
    }

    @Override
    public Optional<CurrentUser> resolve(RequestHeaders headers) {
        String raw = headers.first(HEADER);
        if (raw == null) {
            return Optional.empty();
        }
        String email = raw.strip().toLowerCase(Locale.ROOT);
        if (email.isEmpty() || email.length() > MAX_EMAIL_LENGTH) {
            return Optional.empty();
        }
        return directory.findActiveByEmail(email);
    }
}
