import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.2"
}

group = "com.example"
version = "0.1.0-SNAPSHOT"
description = "Event alerting and notification service - MVP backend"

java {
    toolchain {
        // CLAUDE.md mandates Java 25. Gradle itself may run on any JDK 17+; it locates an
        // installed JDK 25 for compiling and testing (automatic downloads are off, see gradle.properties).
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Versions of every Spring Boot managed library come from the Boot BOM (no extra Gradle plugin).
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    // Web, persistence, validation, schema migrations
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-flyway")

    // Email channel (SMTP)
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // Operations: health probes and metrics, exported in Prometheus format
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-micrometer-metrics")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Databases: H2 for the zero-setup local profile, PostgreSQL for the postgres profile
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // Tests: JUnit 5, AssertJ and Mockito arrive with the Spring Boot test starters
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Lint everything and fail on warnings so deprecations and unchecked casts cannot creep in.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    // The JaCoCo agent extends the boot class path, which makes the JVM print a class-data-sharing
    // warning on every test run; switching sharing off keeps the build output clean.
    jvmArgs("-Xshare:off")
    testLogging {
        events("failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

// ---------------------------------------------------------------------------------------------
// Coverage policy: business logic must reach at least 90 % line coverage.
//
// The HTML/XML report (build/reports/jacoco/test) covers ALL classes, so nothing is hidden.
// The verification gate below covers all classes EXCEPT thin or generated code that holds no
// decisions (see the list). Every exclusion is documented in README.md; add to it deliberately.
// ---------------------------------------------------------------------------------------------
jacoco {
    toolVersion = "0.8.16" // first line to officially support Java 25 is 0.8.14
}

val coverageGateExcludes = listOf(
    "com/example/alerting/AlertingApplication*", // bootstrap only
    "com/example/alerting/web/**",               // thin HTTP adapters and DTOs (covered by API tests, not gated)
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
        csv.required = false
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        sourceSets.main.map { main ->
            main.output.classesDirs.asFileTree.matching { exclude(coverageGateExcludes) }
        }
    )
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
