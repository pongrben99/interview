/**
 * Destinations: a user's saved contact point on one channel (an email address, a Slack webhook).
 * Holds the entity and service, validation by delegating to the channel registry, secret masking
 * and revive-on-recreate of soft-deleted rows. Rules reference destinations by id.
 *
 * <p>Dependency rule: depends on {@code shared} and {@code channel}.
 */
package com.example.alerting.destination;
