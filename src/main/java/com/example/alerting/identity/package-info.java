/**
 * Who is calling. Holds the {@code CurrentUser} port, the header-based stub that resolves a seeded
 * user (demo only; it refuses to start outside the {@code local} profile) and the role guard for
 * admin-only operations. Ownership and role checks are real; only the way the caller is
 * identified is a stub, so real authentication can replace it later (docs/03, section 2.3).
 *
 * <p>Dependency rule: depends on {@code shared} only.
 */
package com.example.alerting.identity;
