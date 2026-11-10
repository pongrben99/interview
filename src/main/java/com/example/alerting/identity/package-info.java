/**
 * Who is calling. Holds {@code CurrentUser}, the {@code IdentityResolver} port, the header-based stub that
 * implements it (demo only: it refuses to start outside the {@code local} profile) and the role guard for
 * admin-only operations. Ownership and role checks are real; only the way the caller is identified is a
 * stub, so real authentication can replace it later (docs/03, section 2.3).
 *
 * <p>The module defines the {@code IdentityDirectory} port for looking users up; the {@code user} module
 * implements it, so this module never depends on the one that owns the users.
 *
 * <p>Dependency rule: depends on {@code shared} only.
 */
package com.example.alerting.identity;
