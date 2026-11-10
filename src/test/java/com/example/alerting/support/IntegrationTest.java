package com.example.alerting.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Marks a test that starts the whole application on random ports.
 *
 * <p>It activates the {@code local} profile (in-memory H2, DRY_RUN channels) together with the
 * {@code test} profile (unique database per context, random management port), so every
 * integration test class runs against the same setup a developer gets from {@code bootRun}.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"local", "test"})
public @interface IntegrationTest {
}
