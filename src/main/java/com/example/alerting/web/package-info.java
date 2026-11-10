/**
 * The HTTP layer: REST controllers under {@code /api/v1}, request and response DTOs, and the
 * global exception handler that renders {@code application/problem+json}.
 *
 * <p>Dependency rule: calls application services only. No other module depends on this one.
 */
package com.example.alerting.web;
