package com.example.alerting.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the HTTP layer, under {@code alerting.http}.
 *
 * @param maxRequestBodyBytes the largest request body the API accepts, in bytes; larger bodies get
 *        {@code 413}. The default is 64 KB, far above any event (docs/02, section 6.5).
 */
@ConfigurationProperties("alerting.http")
public record HttpProperties(@DefaultValue("65536") int maxRequestBodyBytes) {
}
