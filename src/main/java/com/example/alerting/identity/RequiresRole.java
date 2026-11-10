package com.example.alerting.identity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the least privileged role that may call an endpoint.
 *
 * <p>An endpoint without this annotation is open to every identified user. The check itself is
 * {@link RoleGuard}; the web layer applies it before the endpoint runs.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresRole {

    /**
     * The role the caller needs. {@link Role#ADMIN} includes everything {@link Role#USER} may do.
     *
     * @return the required role
     */
    Role value();
}
