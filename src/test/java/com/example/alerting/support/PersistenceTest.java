package com.example.alerting.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks a test that needs the full application context with a real database but no web server.
 *
 * <p>The context uses the {@code local} and {@code test} profiles: in-memory H2 in PostgreSQL mode,
 * migrated by Flyway exactly like a real start. Every test method runs in a transaction that is rolled
 * back afterwards, so tests cannot see each other's data.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"local", "test"})
@Transactional
public @interface PersistenceTest {
}
