package com.example.alerting.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the identity module, under {@code alerting.identity}.
 *
 * @param allowInsecureStubOutsideLocal the deliberately loud override that lets the header identity stub
 *        run outside the {@code local} profile. Leave it off: with it on, anyone who can reach the service
 *        can act as any user. See {@link IdentityStubPolicy}.
 */
@ConfigurationProperties("alerting.identity")
public record IdentityProperties(@DefaultValue("false") boolean allowInsecureStubOutsideLocal) {
}
