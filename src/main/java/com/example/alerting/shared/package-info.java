/**
 * Cross-cutting building blocks used by every other module: the injectable clock, the error model
 * ({@code DomainException}, {@code ValidationException}, {@code FieldError} and the stable
 * {@code ErrorCodes}, which the web layer renders as problem+json) and small text and argument helpers.
 * Pagination and secret masking arrive with the milestones that need them.
 *
 * <p>Dependency rule: depends on no other module of the application.
 */
package com.example.alerting.shared;
