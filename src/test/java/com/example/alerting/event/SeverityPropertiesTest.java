package com.example.alerting.event;

import static com.example.alerting.support.NewEventBuilder.anEvent;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** The severity thresholds are configuration, not code (docs/02 section 6.2). */
class SeverityPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesOnly.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SeverityProperties.class)
    static class PropertiesOnly {
    }

    @Test
    void withoutAnySettingTheDefaultsAreTheDocumentedMappings() {
        runner.run(context -> {
            SeverityProperties properties = context.getBean(SeverityProperties.class);

            assertThat(properties.disaster().magnitudeThresholds()).containsExactly(4.0, 5.0, 6.0, 7.0);
            assertThat(properties.market().changePctThresholds()).containsExactly(1.0, 2.0, 5.0, 10.0);
            assertThat(properties.news().breaking()).isEqualTo(4);
            assertThat(properties.news().developing()).isEqualTo(3);
            assertThat(properties.news().minor()).isEqualTo(1);
            assertThat(properties.news().standard()).isEqualTo(2);
        });
    }

    @Test
    void everyMappingCanBeOverriddenByConfiguration() {
        runner.withPropertyValues(
                        "alerting.severity.disaster.magnitude-thresholds=3.5,4.5,5.5,6.5",
                        "alerting.severity.market.change-pct-thresholds=0.5,1,3,8",
                        "alerting.severity.news.breaking=5",
                        "alerting.severity.news.developing=4",
                        "alerting.severity.news.minor=2",
                        "alerting.severity.news.standard=3")
                .run(context -> {
                    SeverityProperties properties = context.getBean(SeverityProperties.class);

                    assertThat(properties.disaster().magnitudeThresholds()).containsExactly(3.5, 4.5, 5.5, 6.5);
                    assertThat(properties.market().changePctThresholds()).containsExactly(0.5, 1.0, 3.0, 8.0);
                    assertThat(properties.news().breaking()).isEqualTo(5);
                    assertThat(properties.news().developing()).isEqualTo(4);
                    assertThat(properties.news().minor()).isEqualTo(2);
                    assertThat(properties.news().standard()).isEqualTo(3);
                });
    }

    @Test
    void theEventConfigurationBuildsWorkingNormalizersFromTheDefaults() {
        runner.withUserConfiguration(EventConfiguration.class).run(context -> {
            EventNormalizer normalizer = context.getBean(EventNormalizer.class);

            assertThat(normalizer.normalize(anEvent().build()).severity()).isEqualTo(4);
            assertThat(normalizer.normalize(anEvent().category("MARKET").attributes(Map.of("changePct", 12.0)).build()).severity())
                    .isEqualTo(5);
            assertThat(normalizer.normalize(anEvent().category("NEWS").noAttributes().build()).severity()).isEqualTo(2);
        });
    }

    @Test
    void configuredThresholdsReachTheNormalizers() {
        runner.withUserConfiguration(EventConfiguration.class)
                .withPropertyValues("alerting.severity.disaster.magnitude-thresholds=3.0,3.5,4.0,4.5")
                .run(context -> assertThat(context.getBean(EventNormalizer.class)
                        .normalize(anEvent().attribute("magnitude", 4.2).build()).severity()).isEqualTo(4));
    }

    @Test
    void invalidThresholdsStopTheApplicationFromStarting() {
        runner.withUserConfiguration(EventConfiguration.class)
                .withPropertyValues("alerting.severity.disaster.magnitude-thresholds=5,4,3,2")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("alerting.severity.disaster.magnitude-thresholds");
                });
    }
}
