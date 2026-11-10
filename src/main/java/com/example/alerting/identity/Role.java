package com.example.alerting.identity;

/**
 * The role of an application user.
 *
 * <p>Roles are stored as text in the {@code users.role} column.
 */
public enum Role {

    /** A regular user: manages their own rules and destinations and reads their own notifications. */
    USER,

    /** An administrator: operates the system through the admin API, in addition to everything a user can do. */
    ADMIN
}
