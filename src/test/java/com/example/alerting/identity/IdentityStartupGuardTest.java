package com.example.alerting.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * The guard applies {@link IdentityStubPolicy} while the context starts. These tests start only the guard
 * and its settings; {@link IdentityStubStartupTest} proves the same rule on the real application.
 */
@ExtendWith(OutputCaptureExtension.class)
class IdentityStartupGuardTest {

    private static final String OVERRIDE = "alerting.identity.allow-insecure-stub-outside-local=true";

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IdentityProperties.class)
    @Import(IdentityStartupGuard.class)
    static class GuardOnly {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(GuardOnly.class);

    @Test
    void startsInTheLocalProfile() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startsWhenLocalIsOneOfSeveralProfiles() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local", "postgres"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startsWhenNoProfileIsChosenAndLocalIsTheDefaultProfile() {
        // application.yml sets spring.profiles.default=local, which is what `./gradlew bootRun` relies on.
        runner.withInitializer(context -> context.getEnvironment().setDefaultProfiles("local"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void refusesToStartInAnotherProfile() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasStackTraceContaining("Refusing to start")
                            .hasStackTraceContaining("alerting.identity.allow-insecure-stub-outside-local");
                });
    }

    @Test
    void refusesToStartWhenNoProfileAtAllIsActive() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void theOverrideLetsAnotherProfileStartButWarnsLoudly(CapturedOutput output) {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues(OVERRIDE)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(output.getAll())
                            .contains("SECURITY WARNING")
                            .contains("Anyone who can reach this service can act as any user");
                });
    }

    @Test
    void theOverrideIsNotNeededInTheLocalProfileAndStaysQuiet(CapturedOutput output) {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .withPropertyValues(OVERRIDE)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(output.getAll()).doesNotContain("SECURITY WARNING");
                });
    }

    @Test
    void theOverrideIsOffByDefault() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .run(context -> assertThat(context.getBean(IdentityProperties.class).allowInsecureStubOutsideLocal())
                        .isFalse());
    }
}
