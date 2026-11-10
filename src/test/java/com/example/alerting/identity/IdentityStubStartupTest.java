package com.example.alerting.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.alerting.AlertingApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The start-up test of docs/03 P-01: the <em>real</em> application, with all its wiring, refuses to start
 * with the header identity stub outside the {@code local} profile, and only the loudly named override
 * changes that.
 *
 * <p>Each test starts the application on its own in-memory database; there is no web server, so no port is
 * used.
 */
@ExtendWith(OutputCaptureExtension.class)
class IdentityStubStartupTest {

    private static SpringApplicationBuilder application(String profile, String... extraProperties) {
        String database = "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        return new SpringApplicationBuilder(AlertingApplication.class)
                .web(WebApplicationType.NONE)
                .profiles(profile)
                .properties("spring.datasource.url=" + database, "spring.datasource.username=sa",
                        "spring.datasource.password=")
                .properties(extraProperties);
    }

    @Test
    void theApplicationStartsInTheLocalProfile() {
        try (ConfigurableApplicationContext context = application("local").run()) {
            assertThat(context.isRunning()).isTrue();
            assertThat(context.getBeansOfType(IdentityStartupGuard.class)).hasSize(1);
        }
    }

    @Test
    void theApplicationRefusesToStartInAnotherProfile() {
        assertThatThrownBy(() -> application("prod").run())
                .hasStackTraceContaining("Refusing to start")
                .hasStackTraceContaining("alerting.identity.allow-insecure-stub-outside-local");
    }

    @Test
    void theOverrideStartsTheApplicationOutsideLocalWithALoudWarning(CapturedOutput output) {
        try (ConfigurableApplicationContext context =
                     application("prod", "alerting.identity.allow-insecure-stub-outside-local=true").run()) {
            assertThat(context.isRunning()).isTrue();
        }

        assertThat(output.getAll()).contains("SECURITY WARNING");
    }
}
