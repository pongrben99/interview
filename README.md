# Alerting service: MVP backend

An event alerting and notification service. Users define alert **rules** (what they care about) and
**destinations** (an email address, a Slack webhook). **Sources** publish normalized **events**; every
event is matched against all enabled rules, each match becomes a durable **notification**, and a
dispatcher delivers it through a pluggable **channel** (Email and Slack now, anything later),
retrying transient failures and parking permanent ones for an admin.

This repository currently contains the **backend only**; the dashboard comes in a later phase.
The design lives in [`docs/`](docs): [plan of attack](docs/01-PLAN-OF-ATTACK.md),
[architecture](docs/02-ARCHITECTURE-PROPOSAL.md), [security, operations and delivery](docs/03-SECURITY-OPERATIONS-DELIVERY.md).

**Status:** milestones M1 (walking skeleton), M2 (domain model and persistence) and M3 (event ingestion and
the identity stub) are in place. See the milestone list in the plan.

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

## Database

The schema is created by Flyway migrations in `src/main/resources/db/migration`, as described in
[docs/02, section 5](docs/02-ARCHITECTURE-PROPOSAL.md). The same SQL runs on H2 (PostgreSQL mode) and on
PostgreSQL.

| Migration | Content | Loaded in |
|---|---|---|
| `V1__schema.sql` | 11 tables, 8 sequences, unique, foreign-key and CHECK constraints, indexes | every profile |
| `V2__reference_data.sql` | Categories NEWS, MARKET, DISASTER and the three event sources | every profile |
| `db/dev/R__demo_users.sql` | Demo users `admin@`, `alice@` and `bob@example.com` (repeatable and idempotent) | the `local` profile only |

Hibernate validates every entity mapping against this schema at start-up (`ddl-auto=validate`), so a
mapping that does not match the migrations stops the application instead of failing later.

## Identity: the demo stub

There is no login yet. A caller names themselves with the **`X-User-Id`** header, which carries the email
address of a seeded user. The `local` profile seeds `admin@example.com` (role ADMIN) and `alice@example.com`
and `bob@example.com` (role USER).

| Situation | Answer |
|---|---|
| Header missing or blank, unknown user, or deactivated user (all answered identically, so the response does not reveal which addresses exist) | `401` `UNAUTHENTICATED` |
| Known user without the role the endpoint needs (`ADMIN` includes everything `USER` may do) | `403` `FORBIDDEN` |

The role checks are real; only the way the caller is identified is a stub. It sits behind the
`IdentityResolver` port, so real authentication replaces it without touching business code.

**The stub is not secure**: anyone who can reach the service can claim to be anyone. It therefore **fails
closed** ([docs/03, P-01](docs/03-SECURITY-OPERATIONS-DELIVERY.md)): the application refuses to start with it
unless the `local` profile is active. The deliberately loud override
`alerting.identity.allow-insecure-stub-outside-local=true` starts it anyway and logs a security warning.

## REST API

Base path `/api/v1`, JSON in and out. Every endpoint needs an identity (see above). The implemented
endpoints so far:

| Method and path | Role | Purpose |
|---|---|---|
| `POST /api/v1/events` | ADMIN | Publish an event. `201` for a new event, `200` if the same event was accepted before |

More endpoints arrive with the next milestones (docs/02, section 9).

### Publishing an event

```bash
curl -i -X POST http://localhost:8090/api/v1/events \
  -H "X-User-Id: admin@example.com" -H "Content-Type: application/json" \
  -d '{"externalId":"demo:quake-1","category":"DISASTER","title":"M 6.4 earthquake near Hualien, Taiwan","region":"TW","occurredAt":"2026-11-10T08:12:00Z","attributes":{"magnitude":6.4,"tsunami":false}}'
```

Answer: `{"id":1,"status":"RECEIVED","duplicate":false}`. Sending the same request again answers `200` with
`"duplicate":true` and the same id, and changes nothing: events are immutable and unique per source and
external id.

| Field | Required | Rules |
|---|---|---|
| `source` | no | A source code; default `manual`. Unknown: `400 SOURCE_INVALID`. Disabled: `409 SOURCE_DISABLED` |
| `externalId` | no | At most 200 characters. If absent it is derived from the content (`h:` and a SHA-256), so the same payload is still idempotent |
| `category` | yes | An enabled category, exact and case-sensitive (`NEWS`, `MARKET`, `DISASTER`). Otherwise `400 CATEGORY_INVALID` |
| `severity` | see below | Integer 1 to 5 |
| `title` | yes | At most 300 characters; trimmed, control characters removed |
| `summary` | no | At most 2000 characters |
| `url` | no | Absolute `http` or `https` URL, at most 2000 characters |
| `region` | no | Letters, digits, `_` and `-`, at most 50; stored upper-case |
| `occurredAt` | yes | ISO-8601 instant such as `2026-11-10T08:12:00Z`; at most 5 minutes in the future |
| `attributes` | no | Object of scalars (text, number, boolean); keys match `[A-Za-z][A-Za-z0-9_]{0,39}`; at most 8000 characters serialized |

The JSON is strict: an unknown field, a number where text is expected (or the reverse), and a fractional
severity are all `400`. All field problems are reported together in one response.

**Severity.** A supplied `severity` always wins. Otherwise it is derived from the attributes (thresholds are
[configuration](#configuration)):

| Category | Input | Mapping | Derived attributes added |
|---|---|---|---|
| `DISASTER` | `magnitude` | below 4 gives 1, 4 to 4.9 gives 2, 5 to 5.9 gives 3, 6 to 6.9 gives 4, 7 and above gives 5; `tsunami: true` adds 1 (maximum 5) | none |
| `MARKET` | `changePct` | absolute move: below 1 gives 1, below 2 gives 2, below 5 gives 3, below 10 gives 4, else 5 | `moveMagnitudePct`, `direction` (`UP`, `DOWN`, or `FLAT` for exactly zero) |
| `NEWS` | `breaking`, `developing`, `minor` | breaking gives 4, developing 3, minor 1, none of them 2 | none |
| any other | none | `severity` must be supplied | none |

Derived attributes are added only where the sender did not supply the key.

### Errors

Every error is `application/problem+json` with `type`, `title`, `status`, `detail`, a stable machine-readable
`code`, and for invalid input an `errors` list of `field` and `message`. Clients should branch on `code`, never
on the wording.

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Field problems, an unknown field, a value of the wrong type; see `errors` |
| 400 | `MALFORMED_REQUEST` | The body is missing or is not valid JSON |
| 400 | `CATEGORY_INVALID`, `SOURCE_INVALID` | The category or source does not exist (or the category is disabled) |
| 401 | `UNAUTHENTICATED` | No usable identity |
| 403 | `FORBIDDEN` | The role is too weak |
| 404, 405, 415 | `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE` | No such path; wrong method; not JSON |
| 409 | `SOURCE_DISABLED` | The source exists but an admin disabled it |
| 413 | `PAYLOAD_TOO_LARGE` | The body is over 64 KB (also enforced for chunked uploads without a length) |
| 503 | `SERVICE_UNAVAILABLE` | The database is unavailable. `Retry-After` is set; nothing was changed, so retrying is safe |
| 500 | `INTERNAL_ERROR` | A bug. The response carries no internals; the details are in the log |

Problems are reported in layers: a body that cannot be read (bad JSON, unknown field, wrong type) is
reported first; once it reads, the field rules are checked together; then the source and the category; and
last whether the severity can be derived.

### The event simulator

`EventSimulator` generates earthquakes, market moves and news from a seeded random generator (same seed and
clock, same events) and submits them through the same ingestion path as the push API, under the source
`simulator`. It is a service for now; its admin endpoint arrives in M6.

## Configuration

Every key has a default, so none is required.

| Key | Default | Meaning |
|---|---|---|
| `alerting.http.max-request-body-bytes` | `65536` | Largest accepted request body |
| `alerting.severity.disaster.magnitude-thresholds` | `4.0,5.0,6.0,7.0` | Lower magnitude bounds of severity 2 to 5 |
| `alerting.severity.market.change-pct-thresholds` | `1.0,2.0,5.0,10.0` | Lower bounds, in absolute percent, of severity 2 to 5 |
| `alerting.severity.news.breaking` / `developing` / `minor` / `standard` | `4` / `3` / `1` / `2` | Severity per news flag |
| `alerting.simulator.seed` | `42` | Seed of the event simulator |
| `alerting.identity.allow-insecure-stub-outside-local` | `false` | See [Identity](#identity-the-demo-stub); leave it off |

Threshold lists must have exactly four finite, strictly ascending numbers; an invalid value stops the
application at start-up with a message naming the key.

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
- Test layers, each with its own annotation where it needs a context:
  - **Unit tests** for pure logic and entity invariants (no Spring).
  - **`@PersistenceTest`**: the full context without a web server; every test runs in a transaction that is
    rolled back. Used for the schema, constraints and entity mapping.
  - **`@ServiceTest`**: the same context but *not* rolled back, for code that commits on its own or on other
    threads (the event insert, the 50-thread duplicate race). Such a test class cleans up after itself.
  - **`@ApiTest`**: the whole REST API through `MockMvc` with the real filters, identity, validation,
    database and error rendering, and the demo users loaded.
  - **`@IntegrationTest`**: the application on random ports, for what a mock cannot show (Actuator, a
    chunked upload past the size limit).
  - **Architecture rules** of docs/02 section 3.3, checked by a plain-Java source scanner (module
    dependencies, pure classes free of framework types, entities never in the web layer, no direct clock access).
  - **Start-up tests**: the real application refuses to start with the identity stub outside `local`.
- Trivial accessors have no Javadoc of their own: the field they return is documented, and the mapping
  round-trip tests exercise all of them.
- State after M3: 656 tests; business logic at 99.7 % of lines and 99.6 % of branches. The only uncovered
  business-logic lines are two defensive branches that cannot be reached: the missing-SHA-256 catch in
  `ExternalIds` and the unreachable default of the profile switch in `IdentityStartupGuard`.
