# Alerting service: MVP backend

An event alerting and notification service. Users define alert **rules** (what they care about) and
**destinations** (an email address, a Slack webhook). **Sources** publish normalized **events**; every
event is matched against all enabled rules, each match becomes a durable **notification**, and a
dispatcher delivers it through a pluggable **channel** (Email and Slack now, anything later),
retrying transient failures and parking permanent ones for an admin.

This repository currently contains the **backend only**; the dashboard comes in a later phase.
The design lives in [`docs/`](docs): [plan of attack](docs/01-PLAN-OF-ATTACK.md),
[architecture](docs/02-ARCHITECTURE-PROPOSAL.md), [security, operations and delivery](docs/03-SECURITY-OPERATIONS-DELIVERY.md).

**Status:** milestone M1 (walking skeleton) is in place. See the milestone list in the plan.

## Requirements

- **JDK 25** (any vendor). The build compiles and tests with a Java 25 toolchain.
- **No Gradle installation needed.** Use the wrapper (`gradlew`). The first build downloads Gradle 9.9.0
  (checksum-pinned) and the libraries from Maven Central.
- Gradle itself runs on JDK 17 or newer, so `JAVA_HOME` may point to an older JDK as long as a JDK 25 is
  installed where Gradle can find it. `./gradlew -q javaToolchains` lists the JDKs Gradle sees.
- Running Gradle itself on JDK 25 prints a "restricted method" warning from Gradle's own native
  library. It comes from Gradle, not from this project, and is harmless.

## Quick start

```bash
./gradlew bootRun    # starts on the local profile: in-memory H2 database, no external services
./gradlew check      # compiles with warnings as errors, runs all tests, enforces the coverage gate
```

On Windows use `.\gradlew.bat` instead of `./gradlew`.

The application listens on port **8090** (not Spring's usual 8080, which is often taken on developer
machines). Start it on another port with `./gradlew bootRun --args='--server.port=18080'`.

While the application runs, the management endpoints are on the internal port **8081** (bound to
`127.0.0.1`, never on the main port):

| URL | Purpose |
|---|---|
| `http://127.0.0.1:8081/actuator/health` | Overall health (also `/liveness` and `/readiness`) |
| `http://127.0.0.1:8081/actuator/info` | Build information |
| `http://127.0.0.1:8081/actuator/prometheus` | Metrics in Prometheus format |

Everything else Actuator offers (`env`, `beans`, ...) is deliberately not exposed.

## Profiles

| Profile | Purpose | Database | Notes |
|---|---|---|---|
| `local` (default) | Zero-setup development and demos | In-memory H2 in PostgreSQL compatibility mode | Active when no profile is chosen |
| `postgres` | Run against PostgreSQL | PostgreSQL at `localhost:5432` by default | Combine with `local`: `SPRING_PROFILES_ACTIVE=local,postgres`. Override the connection with `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD`. Logs are JSON (ECS). Configuration only so far: not yet exercised against a real PostgreSQL. |

## Project layout

One Gradle module; packages under `com.example.alerting` follow the module boundaries of
[docs/02, section 3](docs/02-ARCHITECTURE-PROPOSAL.md). Every package has a `package-info.java`
that states its responsibility and its dependency rule.

| Package | Responsibility |
|---|---|
| `shared` | Clock, error types, pagination, masking helpers |
| `identity` | `CurrentUser` port, header-based demo stub, role guard |
| `user` | Users and the seeded demo users |
| `event` | Events, categories, sources, ingestion, severity normalizers, simulator |
| `channel` | Channel SPI, registry, per-channel settings |
| `channel.email`, `channel.slack`, `channel.dryrun` | Adapters and the DRY_RUN machinery |
| `destination` | A user's saved contact points |
| `rule` | Alert rules, criteria and conditions |
| `notification` | Outbox, attempts, state machine, retry policy, dispatcher, sweeper |
| `matching` | Candidate selection, rule evaluation, cooldown, fan-out |
| `admin` | Statistics, audit log, operations |
| `web` | REST controllers, DTOs, error mapping |

## Tooling and pinned versions

| Tool | Version | Notes |
|---|---|---|
| Java | 25 (toolchain) | Verified with Zulu 25.0.4 |
| Gradle | 9.9.0 | Wrapper with SHA-256 checksum; 9.1 or newer is needed to run Gradle itself on JDK 25 |
| Spring Boot | 4.1.2 | Current stable line; the Boot BOM manages all other library versions |
| JaCoCo | 0.8.16 | 0.8.14 was the first release with official Java 25 support |
| Hibernate ORM | 7.4.13.Final | Resolved from the Boot BOM |
| Flyway | 12.4.0 | Resolved from the Boot BOM; the PostgreSQL module is on the runtime classpath |
| H2 | 2.4.240 | In-memory database of the local profile |
| Tomcat (embedded) | 11.0.27 | Resolved from the Boot BOM |
| Micrometer | 1.17.2 | With the Prometheus registry |
| Jackson | 3.1.7 | Group `tools.jackson` (Jackson 3, as shipped with Spring Boot 4) |
| Kotlin DSL | Gradle's own | Build scripts are `*.gradle.kts` |

Libraries beyond the stack in `CLAUDE.md` were approved explicitly: Flyway, Spring Mail, Actuator and
Micrometer (with the Prometheus registry), and for tests JUnit 5, Mockito, AssertJ and Spring Test.
Anything else (for example WireMock, GreenMail, Awaitility, ArchUnit) is **not** used.

## Testing and coverage policy

- Business logic must reach **at least 90 % line coverage**, with 100 % as the aim. `./gradlew check`
  fails below 90 %.
- The HTML report covers **all** classes: `build/reports/jacoco/test/html/index.html`
  (XML: `build/reports/jacoco/test/jacocoTestReport.xml`).
- The gate covers all classes except thin code that holds no decisions. Current exclusions, defined in
  `build.gradle.kts`:
  - `AlertingApplication`: bootstrap only.
  - the `web` package: HTTP adapters and DTOs. They are exercised by API tests but not gated.
- Time and randomness are injected, so retry, cooldown and lease logic is tested without sleeping.
