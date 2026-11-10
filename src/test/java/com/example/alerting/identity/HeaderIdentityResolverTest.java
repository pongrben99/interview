package com.example.alerting.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The demo identity: the {@code X-User-Id} header names a user by email address. */
class HeaderIdentityResolverTest {

    private static final CurrentUser ALICE = new CurrentUser(2L, "alice@example.com", "Alice Demo", Role.USER);

    /** A directory that records what it was asked, so we can see exactly what reaches the database. */
    private final List<String> lookups = new ArrayList<>();
    private final IdentityDirectory directory = email -> {
        lookups.add(email);
        return email.equals(ALICE.email()) ? Optional.of(ALICE) : Optional.empty();
    };
    private final HeaderIdentityResolver resolver = new HeaderIdentityResolver(directory);

    private Optional<CurrentUser> resolve(String headerValue) {
        Map<String, String> headers = headerValue == null ? Map.of() : Map.of("X-User-Id", headerValue);
        return resolver.resolve(headers::get);
    }

    @Test
    void aKnownEmailIdentifiesTheUser() {
        assertThat(resolve("alice@example.com")).contains(ALICE);
    }

    @Test
    void theEmailIsStrippedAndLowerCasedBeforeTheLookup() {
        assertThat(resolve("  Alice@Example.COM \t")).contains(ALICE);
        assertThat(lookups).containsExactly("alice@example.com");
    }

    @Test
    void aMissingHeaderIdentifiesNobodyWithoutALookup() {
        assertThat(resolve(null)).isEmpty();
        assertThat(lookups).isEmpty();
    }

    @Test
    void aBlankHeaderIdentifiesNobodyWithoutALookup() {
        assertThat(resolve("")).isEmpty();
        assertThat(resolve("    ")).isEmpty();
        assertThat(lookups).isEmpty();
    }

    @Test
    void anUnknownEmailIdentifiesNobody() {
        assertThat(resolve("nobody@example.com")).isEmpty();
        assertThat(lookups).containsExactly("nobody@example.com");
    }

    @Test
    void aValueLongerThanAnyEmailAddressNeverReachesTheDirectory() {
        assertThat(resolve("a".repeat(HeaderIdentityResolver.MAX_EMAIL_LENGTH + 1))).isEmpty();
        assertThat(lookups).isEmpty();
    }

    @Test
    void aValueOfExactlyTheMaximumLengthIsStillLookedUp() {
        assertThat(resolve("a".repeat(HeaderIdentityResolver.MAX_EMAIL_LENGTH))).isEmpty();
        assertThat(lookups).hasSize(1);
    }

    @Test
    void theHeaderNameIsTheDocumentedOne() {
        assertThat(HeaderIdentityResolver.HEADER).isEqualTo("X-User-Id");
    }
}
