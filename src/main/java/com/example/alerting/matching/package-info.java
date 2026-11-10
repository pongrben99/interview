/**
 * Matching events against rules: candidate selection, the pure rule evaluator, the cooldown
 * decision and the fan-out into notifications, all idempotent per event (docs/02, section 7).
 *
 * <p>Dependency rule: depends on {@code shared}, {@code event}, {@code rule}, {@code destination}
 * and {@code notification}.
 */
package com.example.alerting.matching;
