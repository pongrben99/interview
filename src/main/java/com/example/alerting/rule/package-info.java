/**
 * Alert rules: what a user wants to be told about. Holds the rule entity, the criteria and
 * condition model (regions, keywords, attribute conditions) and the rule service with validation
 * and quotas (docs/02, section 7).
 *
 * <p>Dependency rule: depends on {@code shared}, {@code event} (categories) and
 * {@code destination}. It never calls {@code notification}; it publishes in-process events such
 * as {@code RuleDisabled} instead.
 */
package com.example.alerting.rule;
