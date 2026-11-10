/**
 * Slack channel adapter: posts alerts to an incoming-webhook URL over HTTPS, guards against
 * server-side request forgery with a host allowlist, and classifies HTTP failures as retryable or
 * permanent (docs/02, section 8.6).
 *
 * <p>Dependency rule: implements the {@code channel} SPI; depends on {@code channel} and
 * {@code shared} only.
 */
package com.example.alerting.channel.slack;
