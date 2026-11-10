/**
 * Notifications and their delivery: the outbox entity and the append-only attempt ledger, the
 * pure state machine and retry policy, the dispatcher with its lease-based claim, and the
 * recovery sweeper (docs/02, section 8).
 *
 * <p>Dependency rule: depends on {@code shared}, {@code channel}, {@code destination},
 * {@code event} and, read-only, {@code rule}.
 */
package com.example.alerting.notification;
