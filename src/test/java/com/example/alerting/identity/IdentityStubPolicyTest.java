package com.example.alerting.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.alerting.identity.IdentityStubPolicy.Decision;
import org.junit.jupiter.api.Test;

/** The fail-closed rule of docs/03 P-01, for every combination. */
class IdentityStubPolicyTest {

    @Test
    void theLocalProfileAlwaysAllowsTheStub() {
        assertThat(IdentityStubPolicy.decide(true, false)).isEqualTo(Decision.ALLOWED_IN_LOCAL);
        assertThat(IdentityStubPolicy.decide(true, true)).isEqualTo(Decision.ALLOWED_IN_LOCAL);
    }

    @Test
    void anotherProfileRefusesTheStubUnlessTheOverrideIsSet() {
        assertThat(IdentityStubPolicy.decide(false, false)).isEqualTo(Decision.REFUSED);
    }

    @Test
    void anotherProfileWithTheOverrideAllowsTheStubButFlagsIt() {
        assertThat(IdentityStubPolicy.decide(false, true)).isEqualTo(Decision.ALLOWED_BY_OVERRIDE);
    }

    @Test
    void theLocalProfileIsCalledLocal() {
        assertThat(IdentityStubPolicy.LOCAL_PROFILE).isEqualTo("local");
    }
}
