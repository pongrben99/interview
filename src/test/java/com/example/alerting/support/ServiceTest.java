package com.example.alerting.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Marks a test of a service that commits its own transactions, against the full application context and a
 * real database but no web server.
 *
 * <p>Unlike {@link PersistenceTest} the test method is <em>not</em> wrapped in a transaction that is rolled
 * back: code that runs in {@code REQUIRES_NEW} (the event insert) or on other threads (the race test)
 * commits for real, so a test class using this annotation cleans up after itself.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"local", "test"})
public @interface ServiceTest {
}
