package com.example.alerting.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.alerting.support.SourceScanner.SourceFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link SourceScanner}. Each rule is shown to catch a violation in a small synthetic source
 * tree, and to stay quiet when the code is fine, so the real architecture rules cannot pass by accident.
 */
class SourceScannerTest {

    private static final String BASE = "com.example.alerting";
    private static final SourceScanner SCANNER = new SourceScanner(BASE, Set.of("channel"));

    @TempDir
    Path root;

    private void write(String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void parsesPackageClassNameImportsEntityFlagAndStripsComments() throws IOException {
        write("com/example/alerting/rule/Thing.java", """
                package com.example.alerting.rule;

                import com.example.alerting.event.Severity;
                import static com.example.alerting.shared.Text.ELLIPSIS;
                import com.example.alerting.destination.*;
                import java.util.List;

                /** Javadoc that mentions Instant.now() */
                // a line comment with System.currentTimeMillis()
                @Entity
                public class Thing {
                }
                """);
        write("com/example/alerting/rule/package-info.java", "package com.example.alerting.rule;\n");

        List<SourceFile> files = SCANNER.scan(root);

        assertThat(files).hasSize(1);
        SourceFile file = files.get(0);
        assertThat(file.module()).isEqualTo("rule");
        assertThat(file.packageName()).isEqualTo("com.example.alerting.rule");
        assertThat(file.className()).isEqualTo("Thing");
        assertThat(file.imports()).containsExactlyInAnyOrder("com.example.alerting.event.Severity",
                "com.example.alerting.shared.Text.ELLIPSIS", "com.example.alerting.destination.*", "java.util.List");
        assertThat(file.entity()).isTrue();
        assertThat(file.code()).doesNotContain("Instant.now()").doesNotContain("currentTimeMillis");
        assertThat(file.qualifiedName()).isEqualTo("com.example.alerting.rule.Thing");
    }

    @Test
    void aFileWithoutAPackageDeclarationIsAnError() throws IOException {
        write("Broken.java", "public class Broken { }\n");

        assertThatThrownBy(() -> SCANNER.scan(root))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No package declaration");
    }

    @ParameterizedTest(name = "{0} belongs to {1}")
    @CsvSource(nullValues = "NULL", value = {
            "com.example.alerting.shared.Guards, shared",
            "com.example.alerting.event.Severity.INFO, event",
            "com.example.alerting.channel.ChannelSetting, channel",
            "com.example.alerting.channel.email.EmailChannel, channel.email",
            "com.example.alerting.channel.slack.*, channel.slack",
            "com.example.alerting.channel, channel",
            "com.example.alerting.AlertingApplication, (root)",
            "com.example.alerting, (root)",
            "java.util.List, NULL",
            "com.example.alertingx.Other, NULL"})
    void moduleOfMapsNamesToModules(String name, String expected) {
        assertThat(SCANNER.moduleOf(name)).isEqualTo(expected);
    }

    @Test
    void reportsADependencyThatIsNotAllowed() throws IOException {
        write("com/example/alerting/rule/Thing.java", """
                package com.example.alerting.rule;

                import com.example.alerting.notification.Notification;

                class Thing { }
                """);

        List<String> violations = SCANNER.moduleDependencyViolations(SCANNER.scan(root),
                Map.of("rule", Set.of("shared", "event"), "notification", Set.of()));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).contains("'rule' must not depend on 'notification'")
                .contains("com.example.alerting.notification.Notification");
    }

    @Test
    void acceptsAllowedOwnExternalAndRootImports() throws IOException {
        write("com/example/alerting/rule/Thing.java", """
                package com.example.alerting.rule;

                import com.example.alerting.event.Severity;
                import com.example.alerting.rule.Other;
                import com.example.alerting.AlertingApplication;
                import java.util.List;
                import org.springframework.stereotype.Component;

                class Thing { }
                """);

        assertThat(SCANNER.moduleDependencyViolations(SCANNER.scan(root), Map.of("rule", Set.of("event")))).isEmpty();
    }

    @Test
    void wildcardAndStaticImportsAreChecked() throws IOException {
        write("com/example/alerting/rule/Thing.java", """
                package com.example.alerting.rule;

                import com.example.alerting.notification.*;
                import static com.example.alerting.matching.Helper.help;

                class Thing { }
                """);

        List<String> violations = SCANNER.moduleDependencyViolations(SCANNER.scan(root), Map.of("rule", Set.of()));

        assertThat(violations).hasSize(2);
    }

    @Test
    void channelAdaptersAreSeparateModules() throws IOException {
        write("com/example/alerting/channel/Core.java", """
                package com.example.alerting.channel;

                import com.example.alerting.channel.email.EmailChannel;

                class Core { }
                """);

        List<String> violations = SCANNER.moduleDependencyViolations(SCANNER.scan(root),
                Map.of("channel", Set.of("shared"), "channel.email", Set.of("channel")));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).contains("'channel' must not depend on 'channel.email'");
    }

    @Test
    void aModuleThatIsNotRegisteredIsReported() throws IOException {
        write("com/example/alerting/newmodule/Thing.java", "package com.example.alerting.newmodule;\nclass Thing { }\n");

        List<String> violations = SCANNER.moduleDependencyViolations(SCANNER.scan(root), Map.of());

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).contains("module 'newmodule' is not registered");
    }

    @Test
    void classesInTheBasePackageAreNotChecked() throws IOException {
        write("com/example/alerting/AlertingApplication.java", """
                package com.example.alerting;

                import com.example.alerting.web.Anything;

                class AlertingApplication { }
                """);

        assertThat(SCANNER.moduleDependencyViolations(SCANNER.scan(root), Map.of())).isEmpty();
    }

    @Test
    void pureClassesMustNotImportForbiddenTypes() throws IOException {
        write("com/example/alerting/shared/Pure.java", """
                package com.example.alerting.shared;

                import org.springframework.stereotype.Component;
                import java.util.List;

                class Pure { }
                """);
        write("com/example/alerting/shared/Impure.java", """
                package com.example.alerting.shared;

                import org.springframework.stereotype.Component;

                class Impure { }
                """);

        List<String> violations = SCANNER.pureClassViolations(SCANNER.scan(root),
                file -> file.className().equals("Pure"), List.of("org.springframework", "jakarta."));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).contains("pure class Pure must not import org.springframework.stereotype.Component");
    }

    @Test
    void aPureClassWithAllowedImportsPasses() throws IOException {
        write("com/example/alerting/shared/Pure.java", """
                package com.example.alerting.shared;

                import java.util.List;

                class Pure { }
                """);

        assertThat(SCANNER.pureClassViolations(SCANNER.scan(root), file -> true, List.of("org.springframework"))).isEmpty();
    }

    @Test
    void theWebLayerMustNotImportEntities() throws IOException {
        write("com/example/alerting/rule/AlertRule.java", """
                package com.example.alerting.rule;

                @Entity
                public class AlertRule { }
                """);
        write("com/example/alerting/web/Leak.java", """
                package com.example.alerting.web;

                import com.example.alerting.rule.AlertRule;

                class Leak { }
                """);
        write("com/example/alerting/web/WildcardLeak.java", """
                package com.example.alerting.web;

                import com.example.alerting.rule.*;

                class WildcardLeak { }
                """);
        write("com/example/alerting/web/Fine.java", """
                package com.example.alerting.web;

                import com.example.alerting.rule.RuleService;
                import java.util.List;

                class Fine { }
                """);

        List<String> violations = SCANNER.entityLeaks(SCANNER.scan(root), "web");

        assertThat(violations).hasSize(2);
        assertThat(violations).anySatisfy(v -> assertThat(v).contains("Leak.java").contains("com.example.alerting.rule.AlertRule"));
        assertThat(violations).anySatisfy(v -> assertThat(v).contains("WildcardLeak.java"));
    }

    @Test
    void entitiesMayBeUsedOutsideTheWebLayer() throws IOException {
        write("com/example/alerting/rule/AlertRule.java", "package com.example.alerting.rule;\n@Entity\nclass AlertRule { }\n");
        write("com/example/alerting/matching/Matcher.java", """
                package com.example.alerting.matching;

                import com.example.alerting.rule.AlertRule;

                class Matcher { }
                """);

        assertThat(SCANNER.entityLeaks(SCANNER.scan(root), "web")).isEmpty();
    }

    @Test
    void forbiddenApisAreFoundInCodeButNotInCommentsOrExemptClasses() throws IOException {
        write("com/example/alerting/shared/Bad.java", """
                package com.example.alerting.shared;

                import java.time.Instant;

                class Bad {
                    Instant now() { return Instant.now(); }
                }
                """);
        write("com/example/alerting/shared/Commented.java", """
                package com.example.alerting.shared;

                /** Mentions Instant.now() only in documentation. */
                class Commented {
                    // Instant.now() is not allowed here either
                }
                """);
        write("com/example/alerting/shared/ClockConfig.java", """
                package com.example.alerting.shared;

                import java.time.Clock;

                class ClockConfig {
                    Clock clock() { return Clock.systemUTC(); }
                }
                """);

        List<String> violations = SCANNER.forbiddenApiUsages(SCANNER.scan(root),
                List.of("Instant.now()", "Clock.systemUTC()"), Set.of("ClockConfig"));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).contains("Bad.java").contains("Instant.now()");
    }
}
