package com.example.alerting.event;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the event simulator, under {@code alerting.simulator}.
 *
 * @param seed seeds the random generator: the same seed produces the same sequence of events, which makes
 *        demos and tests reproducible
 */
@ConfigurationProperties("alerting.simulator")
public record SimulatorProperties(@DefaultValue("42") long seed) {
}
