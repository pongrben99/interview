package com.example.alerting;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.alerting.support.SourceScanner;
import com.example.alerting.support.SourceScanner.SourceFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The architecture rules of docs/02 section 3.3, checked against the real source tree.
 *
 * <p>The module dependency map below mirrors the "Dependency rule" of every {@code package-info.java}.
 * Changing a dependency means changing this map on purpose, in the same commit as the code. The checker
 * itself is tested in {@code SourceScannerTest}.
 */
class ArchitectureRulesTest {

    private static final SourceScanner SCANNER = new SourceScanner("com.example.alerting", Set.of("channel"));

    /** Modules that may be depended on by {@code admin} and {@code web} (everything except {@code web}). */
    private static final Set<String> ALL_BUT_WEB = Set.of("shared", "identity", "user", "event", "channel",
            "channel.email", "channel.slack", "channel.dryrun", "destination", "rule", "notification", "matching", "admin");

    private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry("shared", Set.of()),
            Map.entry("identity", Set.of("shared")),
            Map.entry("user", Set.of("shared", "identity")),
            Map.entry("event", Set.of("shared")),
            Map.entry("channel", Set.of("shared")),
            Map.entry("channel.email", Set.of("channel", "shared")),
            Map.entry("channel.slack", Set.of("channel", "shared")),
            Map.entry("channel.dryrun", Set.of("channel", "shared")),
            Map.entry("destination", Set.of("shared", "channel")),
            Map.entry("rule", Set.of("shared", "event", "destination")),
            Map.entry("notification", Set.of("shared", "channel", "destination", "event", "rule")),
            Map.entry("matching", Set.of("shared", "event", "rule", "destination", "notification")),
            Map.entry("admin", ALL_BUT_WEB),
            Map.entry("web", ALL_BUT_WEB));

    /** Pure business logic: no framework, persistence or I/O types. */
    private static final Set<String> PURE_CLASSES = Set.of("Guards", "Text", "Severity", "NotificationStateMachine",
            "NotificationTrigger", "IllegalStateTransitionException", "RoleGuard", "ThresholdScale", "Attributes",
            "Derivation");

    private static final List<String> PURE_FORBIDDEN_IMPORTS = List.of("org.springframework", "jakarta.", "javax.",
            "java.sql", "java.io", "java.nio", "java.net");

    private static final List<String> DIRECT_CLOCK_ACCESS = List.of("Instant.now()", "LocalDateTime.now()",
            "LocalDate.now()", "ZonedDateTime.now()", "OffsetDateTime.now()", "System.currentTimeMillis()",
            "Clock.systemUTC()", "Clock.systemDefaultZone()");

    private static List<SourceFile> files;

    @BeforeAll
    static void scanTheSources() throws IOException {
        files = SCANNER.scan(Path.of("src/main/java"));
    }

    @Test
    void theScanActuallyFoundTheSourceFiles() {
        // Guards against a wrong path: with no files every rule below would pass for nothing.
        assertThat(files).hasSizeGreaterThan(30);
        assertThat(new TreeSet<>(files.stream().map(SourceFile::module).toList()))
                .contains("shared", "event", "rule", "destination", "notification", "channel", "user", "identity", "admin");
    }

    @Test
    void modulesOnlyDependOnTheModulesTheirDependencyRuleAllows() {
        assertThat(SCANNER.moduleDependencyViolations(files, ALLOWED)).isEmpty();
    }

    @Test
    void pureLogicClassesUseNoFrameworkPersistenceOrIoTypes() {
        List<String> violations = SCANNER.pureClassViolations(files, file -> isPure(file.className()), PURE_FORBIDDEN_IMPORTS);

        assertThat(violations).isEmpty();
        // Every class named as pure must really exist, so a rename cannot silently drop it from the rule.
        assertThat(files.stream().map(SourceFile::className).toList()).containsAll(PURE_CLASSES);
    }

    @Test
    void theWebLayerNeverExposesEntities() {
        assertThat(SCANNER.entityLeaks(files, "web")).isEmpty();
    }

    @Test
    void nothingReadsTheClockDirectlyExceptTheClockConfiguration() {
        assertThat(SCANNER.forbiddenApiUsages(files, DIRECT_CLOCK_ACCESS, Set.of("ClockConfig"))).isEmpty();
    }

    private static boolean isPure(String className) {
        return PURE_CLASSES.contains(className) || className.endsWith("Evaluator") || className.endsWith("Policy")
                || className.endsWith("Normalizer");
    }
}
