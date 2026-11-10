/**
 * The channel extension point. Defines the SPI every delivery channel implements
 * ({@code NotificationChannel}, its descriptor, request and result types), the registry that
 * discovers all channels at start-up, and the per-channel admin settings. Adding a channel means
 * adding one adapter; no other module changes (docs/02, section 8.5).
 *
 * <p>Dependency rule: depends on {@code shared} only. Adapters live in the sub-packages and may
 * depend on this package, never the other way around.
 */
package com.example.alerting.channel;
