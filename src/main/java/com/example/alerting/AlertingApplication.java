package com.example.alerting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the alerting service.
 *
 * <p>The service turns <em>events</em> (an earthquake, a market move, a breaking news item) into
 * <em>notifications</em> for the users whose alert rules match, and delivers them through
 * pluggable channels such as Email and Slack. The design is described in
 * {@code docs/02-ARCHITECTURE-PROPOSAL.md}.
 *
 * <p>Running {@code ./gradlew bootRun} without choosing a profile starts the zero-setup
 * {@code local} profile: an in-memory H2 database and channels in {@code DRY_RUN} mode.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AlertingApplication {

    /**
     * Starts the Spring application.
     *
     * @param args command line arguments, passed on to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(AlertingApplication.class, args);
    }
}
