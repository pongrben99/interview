package com.example.alerting.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Marks a test that calls the REST API through {@code MockMvc} against the full application context.
 *
 * <p>Everything real is in play except the HTTP socket: the servlet filters, the identity interceptor,
 * the controllers, the exception handler, the services and an in-memory H2 database migrated by Flyway.
 * The {@code local} profile is active, and the demo users ({@code admin@}, {@code alice@}, {@code bob@}
 * {@code example.com}) are loaded because the Flyway locations include {@code db/dev}.
 *
 * <p>Requests commit to the database, so a test class that counts rows cleans up after itself.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@TestPropertySource(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/dev")
public @interface ApiTest {
}
