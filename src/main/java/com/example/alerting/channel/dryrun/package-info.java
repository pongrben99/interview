/**
 * Shared DRY_RUN machinery for the channel adapters: renders and logs a message instead of
 * sending it, and can inject failures on demand so retries and dead letters can be demonstrated
 * without external services. Only available in the {@code local} profile (docs/02, section 8.6).
 *
 * <p>Dependency rule: depends on {@code channel} and {@code shared} only.
 */
package com.example.alerting.channel.dryrun;
