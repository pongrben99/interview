/**
 * Email channel adapter: delivers alerts over SMTP (LIVE mode) and classifies SMTP failures as
 * retryable or permanent (docs/02, section 8.6).
 *
 * <p>Dependency rule: implements the {@code channel} SPI; depends on {@code channel} and
 * {@code shared} only.
 */
package com.example.alerting.channel.email;
