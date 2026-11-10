package com.example.alerting.shared;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application's single time source.
 *
 * <p>Nothing in the application calls {@code Instant.now()} directly. Components receive this
 * {@link Clock} instead, so retry delays, cooldowns, leases and the stale-event window can be tested
 * by moving a fake clock rather than by sleeping.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /**
     * The production clock: the system clock in UTC.
     *
     * @return a clock reading the real time
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
