/**
 * Operations for administrators: statistics queries, the audit log, and the facade that requeues
 * or cancels notifications, pauses channels and toggles rules and sources (docs/02, section 9).
 *
 * <p>Dependency rule: may read from every module and call the {@code notification} operations.
 */
package com.example.alerting.admin;
