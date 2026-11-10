# 01 — Plan of Attack

> **Status:** DRAFT v0.3 for review · **Phase 1: planning only, no code** · **Branch:** `feature/planning` · **Date:** 2026-11-10
> **Companions:** [02-ARCHITECTURE-PROPOSAL.md](02-ARCHITECTURE-PROPOSAL.md) · [03-SECURITY-OPERATIONS-DELIVERY.md](03-SECURITY-OPERATIONS-DELIVERY.md) · **Project directives:** [CLAUDE.md](../CLAUDE.md)

## 0. How to review this document

- Every assumption has an ID: `A-xx` (assumptions, §10), `Q-xx` (questions only the PM can answer, §11), `RISK-xx` (§9). Architectural choices are `D-xx` in [doc 02 §13](02-ARCHITECTURE-PROPOSAL.md#13-decision-log).
- Everything is **PROPOSED** until you mark it. Reply by ID, for example `A-07 change: use a Slack app`, `A-15 approve`, `Q-03 answer: internal staff only`.
- Items tagged **[APPROVAL]** extend or change the stack in `CLAUDE.md`. They need an explicit yes/no before Phase 2.
- Nothing after Phase 1 starts without your explicit "go" for that milestone (CLAUDE.md rule 1).
- Effort figures for alternative interpretations are rough planning numbers, not commitments.

## 1. TL;DR

**What we build.** An alerting service. Users define *rules* ("DISASTER, severity at least 4, magnitude at least 6, Japan or Taiwan, at most one alert per 5 minutes") and *destinations* (an email address, a Slack webhook). *Sources* publish normalized *events*. Each event is matched against all enabled rules. Every match becomes a durable *notification* that a dispatcher delivers through a pluggable *channel* (Email and Slack now, anything later), retrying transient failures and parking permanent ones for an admin. A static dashboard gives users their own view and admins an operations console.

**Shape of the plan.** Ten milestones (M0–M9): 20 h of planned work plus a 4 h reserve inside the 24 h budget, with a review gate after every milestone. Each milestone ends with green tests and a short report. We do not start the next one without your "go".

**What makes it enterprise-grade (CLAUDE.md rule 4).** Idempotent ingestion and notification creation (database unique keys). A transactional outbox with leases (no lost work, no double-claimed work). Retry with backoff and a full attempt ledger. An extensible channel SPI proven by a third channel in tests. Module boundaries enforced by architecture tests.

**The five interpretations that change the build most — challenge these first:**

| # | Interpretation | If the PM meant otherwise (rough impact) |
|---|---|---|
| 1 | "Important" = a normalized severity 1–5 assigned at ingestion, plus each user's own thresholds (A-03). No editorial curation, no ML. | Editorially curated feed: add a review/publish state to events (about +2 h). |
| 2 | Events come from a push API, a simulator and (stretch) one free public feed (A-04). | Real licensed news/market feeds: about +4–6 h per feed, plus keys and licensing. |
| 3 | Slack = a user-supplied incoming-webhook URL per destination (A-07). | Slack app with OAuth, DMs and channel picker: about +6–8 h and public callback URLs. |
| 4 | Admin view = operations console (A-09). | User/content management: about +3 h. |
| 5 | Identity is a stub behind a port; ownership and role checks are real (A-10). | Real authentication (OIDC/JWT): about +4–6 h. |

**Stack approvals needed:** A-15 (Spring Boot 4.1.x vs 3.5.x), A-16 (extra dependencies), A-18 (Tailwind delivery). [Doc 03](03-SECURITY-OPERATIONS-DELIVERY.md) adds three more: A-23 (field-level encryption), A-24 (observability stack) and A-25 (Docker packaging).

## 2. The brief, decoded

> **Original brief (verbatim):** "We want users to be able to set up alerts so they get notified when something important happens in the world — like breaking news, market movements, natural disasters, that kind of thing. Should work for both email and Slack. Make it flexible enough that we can add more channels later. We need an admin view too."

| Phrase | What is ambiguous | Our reading | If the PM meant otherwise |
|---|---|---|---|
| "users … set up alerts" | Who are the users? What is an alert? Self-service or admin-created? | End users self-manage **alert rules** (what they care about) and **destinations** (where to reach them). A-06, A-10 | Admin-managed alerts: keep the APIs, drop the user UI (about −2 h). |
| "something important happens in the world" | What is an event, where does it come from, who decides "important", how fast? | An **event** is a normalized, immutable fact (category, severity 1–5, time, title, region, attributes). "Important" = source-assigned severity **plus** each user's thresholds. Near real time: first delivery attempt within 10 s at p95. A-03, A-04, A-05, A-12 | Editorial curation: add a publish state (about +2 h). |
| "breaking news, market movements, natural disasters, that kind of thing" | Exhaustive list or examples? Real feeds? | Three seeded categories (NEWS, MARKET, DISASTER); more are data, not code. Real data via push API and simulator; USGS earthquakes as stretch. A-04 | Licensed feeds: about +4–6 h per feed. |
| "get notified" | Delivery guarantee? Duplicates? Alert fatigue? | At-least-once delivery, exactly one notification record per event × rule × destination, retries, per-rule cooldown. A-11 | Exactly-once is not attainable with SMTP/Slack; we document and mitigate. |
| "email and Slack" | Provider? Per-user or org-wide? Slack app or webhook? | Email via SMTP; Slack via per-destination incoming-webhook URL. A-07, A-08 | Slack app: OAuth, token storage, scopes (about +6–8 h). |
| "flexible enough … add more channels later" | What is the unit of extension? | A channel = one SPI implementation plus a descriptor. No change to ingestion, matching, dispatch, schema or UI forms. Proven by a third, test-only channel. R-13 | — |
| "an admin view too" | Admin of what? | Operations console: dashboard, event and notification browsers with attempt history, retry/cancel dead letters, pause channels, toggle rules and sources, publish/simulate events, audit log. A-09 | User/content management (about +3 h). |
| (not stated) | Authentication, scale, compliance, retention, i18n, deployment | Defaults in A-10 … A-14. Compliance (unsubscribe, GDPR) is explicitly out of scope (§7). | Ask the PM (§11). |

## 3. What we are building

A user registers **destinations** (an email address, a Slack webhook) and defines **rules** that say which events they care about and which destinations to use. **Sources** publish **events**. For each event the system finds the matching rules and creates one **notification** per matched rule × destination. A **dispatcher** delivers each notification through the right **channel** adapter, retries transient failures with backoff, and parks permanent failures as dead letters. The **admin** watches the pipeline and can intervene.

Priority: **M** = must, **S** = should, **C** = could (cut first).

| ID | Requirement | Pri | Milestone |
|---|---|---|---|
| R-01 | A user can create, edit, enable/disable and delete alert rules (name, category, min severity, regions, keywords, attribute conditions, cooldown, ≥ 1 destination). | M | M4 |
| R-02 | A user can manage destinations (EMAIL address, SLACK webhook URL); each is validated by its channel on save. | M | M4 |
| R-03 | A user can see their own notification history and status. | M | M6 |
| R-04 | Events can be published through an authenticated push API; publishing the same event twice is harmless (idempotent). | M | M3 |
| R-05 | A simulator can generate realistic events for all three categories on demand. | S | M3, M6 |
| R-06 | A scheduled connector pulls real earthquakes from USGS. | C | stretch |
| R-07 | Each event yields exactly one notification per matched rule × destination; re-processing an event never creates duplicates. | M | M4 |
| R-08 | Rules honor category, min severity, regions, keywords, attribute conditions and cooldown. | M (cooldown S) | M4 |
| R-09 | Events older than the freshness window are stored but never notified. | S | M4 |
| R-10 | Notifications are delivered asynchronously through a channel SPI; Email and Slack are implemented. | M | M5 |
| R-11 | Transient failures retry with exponential backoff and jitter; permanent failures go to DEAD; every attempt is recorded. | M | M5 |
| R-12 | A crashed or stalled worker never loses or double-claims a notification (leases and a recovery sweeper). | M | M5 |
| R-13 | A new channel can be added without modifying core packages (proved by a test). | S | M5 |
| R-14 | Admin dashboard: counts by status and channel, queue depth, oldest pending age, success rate, recent failures. | M | M6, M7 |
| R-15 | Admin can browse and inspect events and notifications, including attempt history. | M | M6, M7 |
| R-16 | Admin can requeue/cancel notifications, pause/resume channels, enable/disable rules and sources, publish/simulate events; all admin actions are audited. | M (audit S) | M6, M7 |
| R-17 | One-command local run on in-memory H2; PostgreSQL profile is optional. | M (Postgres C) | M1, M9 |
| R-18 | Observability: structured logs with correlation ids, metrics, health indicators. | S | M5, M8 |
| R-19 | Security basics: identity stub with real ownership/role checks, secret masking, SSRF-safe Slack URLs, input validation, output escaping. | M | M3–M5, M8 |
| R-20 | Documentation: README, API reference, as-built architecture notes, demo script, known limitations. | M | M9 |

## 4. Deliverables

| # | Deliverable | Done when |
|---|---|---|
| D1 | Runnable application (Gradle, Java 25, Spring Boot) | On a clean clone `./gradlew check` is green and `./gradlew bootRun` starts on in-memory H2 with seed data. No Docker, Node or network needed. |
| D2 | Event pipeline: ingestion → matching → outbox → dispatch | Demo scenarios DS-1 … DS-5 (§8.5) pass as automated end-to-end tests. |
| D3 | Channel SPI, Email adapter, Slack adapter, DRY_RUN mode, and a test-only third channel | Adapter contract tests and the extension-proof test are green. |
| D4 | REST API `/api/v1` for users and admins | Every endpoint in [doc 02 §9](02-ARCHITECTURE-PROPOSAL.md#9-api-surface) is implemented, validated, role-checked, and returns `application/problem+json` errors. |
| D5 | Static dashboard: User view and Admin view | The manual walkthrough of DS-1 … DS-5 is recorded; no console errors; works offline (if A-18 is approved). |
| D6 | Test suite and evidence | The pyramid in §8.2 exists; JaCoCo report; failure-injection suite; load-smoke numbers recorded with their caveats. |
| D7 | Documentation | README (run, test, configure), API reference plus `.http` request collection, as-built architecture notes, demo script, known limitations. |
| D8 | Process record | Assumption register with final statuses; one PR per milestone with a report; this plan and doc 02 updated in place with a change log. |

## 5. Working agreement

1. **Directives (from CLAUDE.md).** No production code without an explicit phase "go". Small, modular changes tied to a milestone. Every architectural assumption is stated so you can challenge it. Resilience first: idempotency, extensible channels, retry/failure tracking, separation of concerns.
2. **Milestone loop.** (a) Restate scope and the assumptions it touches. (b) Write tests first or alongside. (c) Implement in small commits. (d) `./gradlew check` is green. (e) Put a milestone report in the PR description: what changed, evidence, assumptions touched, deviations, next step. (f) **Stop and wait for your "go".**
3. **Git.** One branch and one PR per milestone (`feature/m1-scaffold`, `feature/m2-persistence`, …), branched from an up-to-date `main`. Imperative commit messages. No force-push. No secrets in the repo. You merge.
4. **Stack changes.** Any dependency or tool not listed in CLAUDE.md goes through an **[APPROVAL]** item (A-15, A-16, A-18).
5. **Assumption protocol.** A new assumption gets an ID and enters the register before code depends on it. A changed assumption is edited in place and logged in §13.
6. **Definition of Done (every milestone).** Compiles cleanly. New behavior has tests. `./gradlew check` is green, including architecture tests. No unexplained TODOs. Docs and the assumption register are updated. The milestone's exit criteria are demonstrated, not claimed.
7. **What the agent will not do.** Invent requirements silently. Add dependencies without approval. Write production code during planning. Touch branches other than the current milestone's. Enter credentials anywhere.

## 6. Milestones within the 24-hour budget

| ID | Milestone | Outcome | Est. (h) | Cum. (h) | Gate: what you review |
|---|---|---|---|---|---|
| M0 | Plan & disambiguate | Docs 01 and 02; assumptions registered | 1.0 | 1.0 | G0: approve/challenge assumptions |
| M1 | Scaffold & walking skeleton | Builds and boots on JDK 25; health UP; Flyway baseline; test tooling wired; version spike done | 1.0 | 2.0 | G1: pinned versions |
| M2 | Domain model & persistence | Full schema via Flyway; entities and repositories; notification state machine; constraint tests | 2.0 | 4.0 | G2: schema vs doc 02 §5 |
| M3 | Event ingestion & identity stub | Idempotent push API; severity normalizers; simulator service; `CurrentUser` stub and role guard; problem+json errors | 2.0 | 6.0 | G3: event contract |
| M4 | Rules, destinations & matching | Channel SPI contract and registry (validate-only); destinations and rules APIs; matcher; fan-out; cooldown; stale-event policy; async trigger and recovery | 3.5 | 9.5 | G4: matching semantics |
| M5 | Dispatch & channels | Dispatcher with leases; retry policy; sweeper; Slack and Email adapters; DRY_RUN; extension proof; metrics | 3.5 | 13.0 | G5: failure semantics |
| M6 | Admin & read APIs | Stats, browsers, requeue/cancel, channel/source/rule toggles, user list, simulator endpoint, audit log, user history | 1.5 | 14.5 | G6: API contract frozen |
| M7 | Frontend | User view and Admin view | 3.0 | 17.5 | G7: UI walkthrough |
| M8 | Hardening & end-to-end | E2E scenarios, load smoke, security checks, graceful shutdown | 1.5 | 19.0 | G8: evidence review |
| M9 | Docs & demo | README, API reference, as-built notes, demo script, clean-clone check | 1.0 | 20.0 | G9: final acceptance |
| — | Reserve | Your review time, slippage, spike overruns | 4.0 | 24.0 | — |

### Milestone details

**M1 — Scaffold & walking skeleton (1.0 h).**
*Scope:* Gradle wrapper ≥ 9.1 and a Java 25 toolchain. Spring Boot line per A-15 after a ≤ 30-minute compatibility spike (Gradle, Boot, Mockito/ByteBuddy, JaCoCo, ArchUnit, WireMock, GreenMail on JDK 25). Package skeleton from doc 02 §3. Profiles `local` (H2 in PostgreSQL mode, DRY_RUN channels) and `postgres`. Flyway baseline. Actuator health. JUnit, AssertJ, ArchUnit, JaCoCo wired. `.gitignore` additions. README stub.
*Exit:* on a clean clone `./gradlew check` is green and `bootRun` reports health UP. Chosen versions are pinned and recorded in the PR.

**M2 — Domain model & persistence (2.0 h).**
*Scope:* Flyway V1 (all tables, constraints, indexes, sequences), V2 reference data (categories, sources, channel settings), dev-only demo users. Entities and Spring Data repositories. Pure notification state machine. Injected `Clock`.
*Exit:* migrations apply on H2 (PostgreSQL mode). Unique, foreign-key and CHECK constraints are proven by tests. A table-driven test covers every legal and illegal state transition. Schema reviewed against doc 02 §5.

**M3 — Event ingestion & identity stub (2.0 h).**
*Scope:* Normalized event contract and validation. `POST /api/v1/events` with category/source resolution, derived `externalId`, race-safe idempotent insert. Severity normalizers (earthquake magnitude, market change, news flags). Deterministic simulator service. `CurrentUser` port, header-based stub, role guard. Problem+json error model.
*Exit:* API tests for 201 (new), 200 (duplicate, same id), 400 (field errors), 401 (no identity), 403 (not admin). A 50-thread duplicate race yields exactly one row. Normalizer boundary tests pass.

**M4 — Rules, destinations & matching (3.5 h).**
*Scope:* Channel SPI contract, registry and descriptors (validation only; sending arrives in M5). Destinations API (CRUD, revive-on-recreate, masking, quotas, SSRF allowlist for Slack URLs). Rules API (CRUD, validation, enable/disable, soft delete, cancel pending notifications on disable). Candidate query, pure `RuleEvaluator`, cooldown via atomic conditional update (SUPPRESSED rows), fan-out, idempotent re-match, stale-event policy, async trigger after commit, unmatched-event sweeper.
*Exit:* ≥ 40 table-driven evaluator cases (boundaries, missing fields, type mismatches, unicode keywords). Matcher integration tests: fan-out count, cooldown, re-run five times creates no new rows, two matchers racing on one event produce one set of rows. Ownership tests return 404 for foreign resources. Stale events are not notified.

**M5 — Dispatch & channels (3.5 h).**
*Scope:* Dispatcher (tick and drain loop, lease claim, send outside any transaction, fenced finalize, per-channel bulkheads on virtual threads). Retry policy (pure, injected randomness), `Retry-After`, error classification, attempt ledger, recovery sweeper for stale leases, channel pause, max-age expiry of old notifications. Slack adapter, Email adapter, DRY_RUN adapters with failure injection. Metrics. Extension-proof test with a test-only third channel.
*Exit:* scripted-channel suite covers success; transient twice then success; permanent to DEAD; unexpected exception treated as transient; attempts exhausted to DEAD; paused channel holds work; lease-expiry recovery. Four competing dispatchers × 500 notifications send each exactly once. WireMock (Slack) and GreenMail (Email) tests cover every classification row in doc 02 §8.6. ArchUnit confirms core packages do not depend on adapter packages.

**M6 — Admin & read APIs (1.5 h).**
*Scope:* Everything in the Admin and User read sections of doc 02 §9 not yet built: stats, event and notification browsers with filters and pagination, requeue/cancel, channel and source toggles, rule overview and disable, user list/deactivate, simulator endpoint, audit log, user notification history.
*Exit:* API tests including role boundaries (a USER cannot call admin endpoints), audit rows written for every admin action, pagination bounds enforced.

**M7 — Frontend (3.0 h).**
*Scope:* Static single-page app (vanilla ES modules, prebuilt Tailwind CSS). User view: rules list and editor, destinations with forms generated from channel descriptors, notification history. Admin view: dashboard (auto-refresh), events, notifications with attempt drawer and requeue/cancel, channels and sources, rule overview, audit log, publish/simulate. Loading, empty and error states. "View as" identity switcher.
*Exit:* manual walkthrough of DS-1 … DS-5 recorded in the PR; no console errors; keyboard-navigable; usable at narrow widths.

**M8 — Hardening & end-to-end (1.5 h).**
*Scope:* End-to-end tests of DS-1 … DS-5 through HTTP with WireMock/GreenMail. Load smoke. Security checks (secret masking, SSRF, escaping, log capture). Graceful shutdown (stop claiming, finish in-flight). Configuration validation tests. Fix list.
*Exit:* full `./gradlew check` green and re-run three times without flakes. Load-smoke numbers recorded. Known-limitations list updated.

**M9 — Docs & demo (1.0 h).**
*Scope:* README, API reference and `.http` collection, as-built architecture notes (diff against the proposal), demo script, final assumption register.
*Exit:* a fresh clone in a scratch directory runs `./gradlew check` and `bootRun`, and the demo script works exactly as written.

### Stretch backlog (only when ahead of schedule, each needs your OK)

| Item | Est. (h) |
|---|---|
| USGS earthquake poller (real public feed through the poll SPI) | 1.0 |
| PostgreSQL profile and Testcontainers integration tests | 1.0 |
| Destination "test send" and rule "preview against last 24 h of events" | 1.0 |
| Per-channel circuit breaker | 0.75 |
| Generated OpenAPI via springdoc | 0.5 |
| Headless UI smoke test | 1.0 |

### Slip checkpoints and cut list

Checkpoints after M4 (hour 9.5), M5 (hour 13.0) and M7 (hour 17.5). If cumulative slip exceeds 1.5 h at any checkpoint, cut in this order:

1. Stretch items (never started early).
2. UI polish: plain tables, no auto-refresh.
3. Audit-log UI (keep the table and API).
4. Simulator variety: one generator per category.
5. SUPPRESSED rows: keep a counter only.
6. Metrics beyond the basics.

**Never cut:** idempotency, retry/failure tracking, the channel SPI, and the tests for invariants I-1 … I-9 (§8.3).

## 7. Intentionally out of scope (and why)

| Item | Why it is out | What we do instead / extension path |
|---|---|---|
| Real authentication and authorization (OIDC, JWT, passwords, SSO, MFA) | Not in the brief; large surface; not what is being evaluated | Identity stub behind a `CurrentUser` port; ownership and role checks are real. Swap in Spring Security later. The stub fails closed outside the `local` profile, and the production path is sketched in [doc 03 §2.3](03-SECURITY-OPERATIONS-DELIVERY.md#23-identity-and-access). |
| Multi-tenancy, organizations, teams, shared rules | Adds a tenant dimension to every table and query | Single tenant. Add `tenant_id` later (doc 02 §12). |
| Slack app (OAuth install, DMs, channel picker, slash commands, interactive buttons) | Needs app registration, secrets, public callback URLs | Incoming webhooks give most of the value for none of the setup. |
| Email compliance and deliverability (SPF/DKIM/DMARC, bounces, complaints, unsubscribe, verified recipients, template designer) | Infrastructure and legal topics, not application logic | SMTP with a configured sender and simple templates. Destination ownership unverified (A-08, RISK-08). |
| Licensed or paid real-time news and market feeds | Cost, keys, licensing, rate limits | Push API and simulator; optional USGS poller; same poll SPI admits more. |
| "Smart" importance: ML/NLP scoring, summarization, clustering, cross-source near-duplicate detection | Research-grade, unbounded | Rule-based thresholds and keywords; exact dedupe on (source, externalId). |
| Geospatial matching (radius, polygon, PostGIS) | Needs geocoding and spatial indexes | Region codes and attribute conditions (for example `attributes.place` CONTAINS "Japan"). |
| Digests, quiet hours, timezone-aware scheduling, snooze, per-user preferences | Not in the brief; each is a feature | Per-rule cooldown only. |
| Rich message customization: per-user templates, i18n, images, attachments | Not in the brief | One plain-text and simple-HTML template per channel, English only. |
| Delivery receipts, read tracking, acknowledgements, escalation policies | Scope creep toward an incident-management tool | Attempt ledger records provider acceptance only. |
| Message brokers and distributed infrastructure (Kafka, RabbitMQ, Redis, Kubernetes, Terraform) | A database outbox meets the envelope (A-12) with far less to operate | `NotificationQueue` seam keeps a broker swap contained (doc 02 §12). A Docker image, docker-compose and reference Kubernetes manifests are proposed in [doc 03 §4](03-SECURITY-OPERATIONS-DELIVERY.md#4-delivery-how-we-ship-it); Terraform stays out. |
| Scale proofs and tuning (millions of rules, sharding, caches) | Beyond the envelope in A-12 | Load smoke only, reported with caveats. |
| Data lifecycle: retention and archival, GDPR export/erasure, encryption of secrets at rest | Compliance programs, not a 24 h task | Masking and no-logging of secrets; documented risk (RISK-08). Optional purge job later. Field-level encryption of destination secrets is proposed in [doc 03 §5](03-SECURITY-OPERATIONS-DELIVERY.md#5-plan-impact) (P-05). |
| API rate limiting and abuse protection | Not in the brief | Request-size limits and per-user quotas only. |
| Mobile apps, push notifications, SMS, voice | These are future channels | The SPI supports them; the third test channel proves it. |
| Frontend frameworks and build tooling (React, Vite, TypeScript), SSR, theming | CLAUDE.md mandates vanilla JS + Tailwind served statically | Vanilla ES modules, no bundler (A-18). |
| Admin user management beyond list and deactivate (invites, onboarding, billing, moderation) | Not implied by "admin view" (A-09) | Seeded demo users; deactivate/activate only. |
| High availability, backups, disaster recovery | Out of scope for a take-home | Multi-node safety by design (leases); operations notes in README. |
| Generated OpenAPI spec | Dependency and version-compatibility risk for little reviewer value | Hand-written API reference plus `.http` collection; springdoc is a stretch item. |

## 8. Verification strategy

### 8.1 Principles

1. Prove behavior at the lowest level that can prove it. Keep the pyramid bottom-heavy.
2. Inject time and randomness (fixed `Clock`, seeded jitter) so retry, cooldown, lease and stale-event logic are tested without sleeping.
3. Every external boundary is a port with a fake (a scripted channel) and a real adapter tested against a simulator (WireMock, GreenMail).
4. Failure injection is first-class: timeouts, 5xx, 4xx, 429 with `Retry-After`, malformed responses, crashes mid-flight, lost leases.
5. Invariants (§8.3) are asserted directly, not inferred from happy paths.
6. Asynchronous behavior is awaited with bounded Awaitility timeouts. `Thread.sleep` is banned in tests.

### 8.2 Test pyramid

| Level | What it proves | Tooling | Target size | Runs |
|---|---|---|---|---|
| Unit (pure logic) | Rule evaluator, keyword matching, severity normalizers, retry/backoff math, notification state machine, destination validation, cooldown | JUnit 5, AssertJ, parameterized tests, fixed `Clock`, seeded random | ~150 | Every build |
| Persistence/integration | Migrations; unique, FK and CHECK constraints; repositories; claim and finalize queries; transaction behavior; concurrent matchers and dispatchers | Spring Boot test, H2 in PostgreSQL mode (PostgreSQL via Testcontainers is a stretch item) | ~40 | Every build |
| Adapter contract | Slack and Email adapters against simulated providers: success, every failure class, timeouts, malformed responses, escaping | WireMock, GreenMail | ~30 | Every build |
| API | Status codes, validation, problem+json, ownership and role boundaries, pagination | Spring Boot test with a random port and a REST client | ~60 | Every build |
| Architecture | Layering; no core → adapter dependencies; no cycles between modules; pure-logic classes free of framework imports | ArchUnit | ~8 rules | Every build |
| End-to-end scenarios | DS-1 … DS-5 through HTTP including asynchronous delivery | Spring Boot test, Awaitility, WireMock, GreenMail | 5–8 | Every build (≤ 60 s) |
| UI checks | Core user and admin flows against the running app | Manual checklist (§8.8); headless smoke test is a stretch item | Checklist | Before release |
| Non-functional smoke | Ingest-to-first-attempt latency, deliveries per second, heap | Scripted load test class excluded from `check` | 1–2 runs | On demand |

Sizes are targets, not claims.

### 8.3 Invariants — what must never break

| ID | Invariant | Proven by |
|---|---|---|
| I-1 | At most one notification per (event, rule, destination). | Unique-constraint test; matcher re-run test; two-matcher race test |
| I-2 | An accepted event is never lost: a 2xx response means a durable event row, and matching eventually happens. | Crash-injection test: fail the matcher after commit, the sweeper matches the event |
| I-3 | A notification is never being sent by two workers at once. | Four dispatchers × 500 notifications test; lease-fencing test |
| I-4 | Permanent failures are never retried; transient failures retry at most N times with backoff. | Scripted-channel tests; backoff schedule table test |
| I-5 | Every attempt is recorded: once a notification is no longer IN_FLIGHT, the number of attempt rows equals `attempt_count`. | Ledger assertion inside every dispatcher test |
| I-6 | Pausing or disabling a channel never loses notifications. | Pause/resume test |
| I-7 | Secrets never appear in API responses or logs. | Masking tests; log-capture assertion in end-to-end tests |
| I-8 | Adding a channel requires no change to core packages. | Extension-proof test plus ArchUnit rule |
| I-9 | Only the owner (or an admin) can read or change a user's rules, destinations and notifications. | API authorization matrix tests |

### 8.4 Requirement traceability

| Requirement | Verified by | Milestone |
|---|---|---|
| R-01 | Rule API tests (CRUD, validation, ownership); soft-delete and cancel-pending tests | M4 |
| R-02 | Destination API tests (per-channel validation, revive, masking, quotas) | M4 |
| R-03 | API test: a user sees only their own notifications; filters; pagination | M6 |
| R-04 | API tests for 201/200/400/401/403; 50-thread duplicate race | M3 |
| R-05 | Simulator determinism test (same seed, same events); admin endpoint test | M3, M6 |
| R-06 | Mapper test against a recorded sample payload; poller test with WireMock | stretch |
| R-07 | Unique-constraint test; matcher re-run ×5; two-matcher race | M4 |
| R-08 | ≥ 40 table-driven evaluator tests; cooldown tests with a fixed clock | M4 |
| R-09 | Matcher test with an old event: SKIPPED_STALE, no notifications | M4 |
| R-10 | Slack and Email contract tests; DS-1 … DS-3 | M5, M8 |
| R-11 | Scripted-channel suite: backoff schedule, DEAD paths, ledger invariant | M5 |
| R-12 | Lease-expiry recovery test; four-dispatcher no-duplicate test | M5 |
| R-13 | Extension-proof test (third channel) plus ArchUnit rule | M5 |
| R-14 | Stats API tests with seeded states; UI walkthrough | M6, M7 |
| R-15 | List and detail API tests with filters; UI walkthrough | M6, M7 |
| R-16 | Requeue/cancel/pause tests with audit rows and role boundaries; DS-4 | M6, M8 |
| R-17 | Startup smoke test; clean-clone run on JDK 25 | M1, M9 |
| R-18 | Metric assertions; health indicator test; log-capture secret check | M5, M8 |
| R-19 | Authorization matrix; SSRF, masking and escaping tests | M3–M5, M8 |
| R-20 | Clean-clone walkthrough of README and demo script | M9 |

### 8.5 Demo scenarios (acceptance tests)

| ID | Scenario | Expected |
|---|---|---|
| DS-1 | **Earthquake to Slack.** A user adds a Slack destination and the rule "DISASTER, severity ≥ 4, magnitude ≥ 6". An admin publishes an M6.4 quake, then an M5.1 quake. | The Slack stub receives exactly one message for the M6.4 event within seconds; the notification is SENT with one attempt; the M5.1 event creates no notification; the admin dashboard shows +1 SENT. |
| DS-2 | **Market move to email.** A rule "MARKET, symbol = AAPL, moveMagnitudePct ≥ 3" with an email destination. Events: AAPL −3.4 %, AAPL +2.9 %. | One email arrives in the mail stub (for −3.4 %); none for +2.9 %. |
| DS-3 | **News keyword to both channels with cooldown.** A rule "NEWS, keyword ceasefire, severity ≥ 3, cooldown 300 s" with Slack and email destinations. Two matching events one minute apart. | First event: two notifications (one per destination), both SENT. Second event: two SUPPRESSED rows with reason COOLDOWN, visible to the user and the admin. |
| DS-4 | **Failure and recovery.** The Slack stub answers 503 twice, then 200. A second event hits a webhook that answers 404. | First: RETRY_SCHEDULED twice with growing delays, then SENT; three attempts recorded. Second: DEAD after one attempt (permanent). After the stub is fixed, an admin requeues it and it is SENT; the audit log shows the action. |
| DS-5 | **Idempotency and extensibility.** The same event is posted 10 times. A test-only third channel is registered. | One event row, no duplicate notifications. A rule can target the third channel and its notifications are delivered with zero changes to core packages. |

### 8.6 Quality gates

- `./gradlew check` runs unit, integration, adapter, API, architecture and end-to-end tests.
- Coverage: JaCoCo line coverage ≥ 80 % on the `rule`, `matching`, `notification` and `channel` packages. Not a global number: it targets where defects hurt most.
- Compiler warnings are treated as errors for main code if the JDK 25 toolchain allows it.
- No flaky tests are tolerated: the full suite is re-run three times at M8.

### 8.7 Non-functional smoke

A scripted load test (excluded from `check`) publishes N events against M rules and records p50 and p95 of ingest-to-first-attempt latency, deliveries per second, and peak heap. The numbers are reported with their environment (laptop, H2, DRY_RUN) and compared with the envelope in A-12. They are an indicator, not a benchmark.

### 8.8 UI checks (manual, M7)

Keyboard navigation and visible focus; empty, loading and error states; narrow viewport; confirmation on destructive actions; no console errors; secrets masked; works with the network disabled (if A-18 is approved).

### 8.9 Process verification (transparency)

Every PR carries a milestone report that lists the assumptions it touched. The assumption register and this plan are updated in place, with a change log. At M9 a fresh clone in a scratch directory runs `./gradlew check`, `bootRun` and the demo script exactly as documented, and the tool versions used are recorded.

### 8.10 What we will not claim

- No benchmark at scale (A-12 is an envelope, smoke-tested only).
- Slack and SMTP behavior is verified against simulators, not real providers. A real-provider smoke test happens only if you supply a sandbox webhook and SMTP target.
- H2 is not PostgreSQL. PostgreSQL verification is a stretch item; portable SQL rules (A-17) reduce but do not remove the gap.

## 9. Risks

| ID | Risk | L | I | Mitigation | Trigger |
|---|---|---|---|---|---|
| RISK-01 | JDK 25 compatibility of build and test tooling (Gradle ≥ 9.1, Mockito/ByteBuddy, JaCoCo, ArchUnit, WireMock) | M | H | M1 spike (≤ 30 min) pins versions. Fallbacks: compile with a JDK 25 toolchain under an older Gradle JVM, or (with approval) JDK 21. | Spike fails |
| RISK-02 | Spring Boot 4.x differences (modular starters, Jackson 3) cost time | M | M | Stay with mainstream APIs; time-boxed spike; fallback 3.5.x (A-15) | Spike exceeds 30 min |
| RISK-03 | M4 and M5 (the densest milestones) overrun | M | H | Slip checkpoints and cut list (§6); stretch items never start early | Slip > 1.5 h at a checkpoint |
| RISK-04 | H2 behaves differently from PostgreSQL (locking, dialect) | M | M | Portable SQL rules (A-17); H2 in PostgreSQL mode; concurrency tests assert invariants, not timing; PostgreSQL run as stretch | A test passes only on H2 |
| RISK-05 | Real Slack, SMTP or USGS unavailable to reviewers | H | M | DRY_RUN by default; adapters tested against simulators; LIVE is configuration only | — |
| RISK-06 | Scope creep from a vague brief | H | M | Assumption register, scope-out table, milestone gates | A "small" request arrives mid-milestone |
| RISK-07 | Duplicate delivery after a crash (at-least-once) | L | M | Accepted and documented (A-11). Email `Message-ID` and footer reference. Lease ≥ 6 × adapter timeout. | — |
| RISK-08 | Secret handling: Slack webhook URLs are bearer secrets | M | H | Masking everywhere, never logged, SSRF allowlist. Encryption at rest is out of scope and called out. | A secret appears in a log or response |
| RISK-09 | Polling dispatcher adds latency and database load | L | L | 1 s tick with a drain loop; indexed claim query; queue-age metric; upgrade path in doc 02 §12 | p95 above 10 s in load smoke |
| RISK-10 | Frontend takes more time than budgeted (no framework) | M | M | Two page shells, shared components, descriptor-driven forms; cut list covers polish | M7 exceeds 3.5 h |

L = likelihood, I = impact (L/M/H).

## 10. Assumption register

Status values: PROPOSED, APPROVED, CHANGED, REJECTED. Everything starts as PROPOSED.

| ID | Assumption | Why we think so | If wrong | Status |
|---|---|---|---|---|
| A-01 | **Budget.** 24 h is elapsed wall-clock time including your review waits. Plan = 20 h of work + 4 h reserve. | The brief says "24-hour budget" without saying whose hours. | If it is 24 h of pure work time we gain slack and pull stretch items in. | PROPOSED |
| A-02 | **Evaluation mode.** A reviewer reads the repo and docs and runs the app with only JDK 25 (Gradle wrapper): no Docker, Node or network for the default demo. | CLAUDE.md asks for zero-friction evaluation. | If Docker is acceptable we add a docker-compose profile earlier. | PROPOSED |
| A-03 | **"Important".** Importance = a normalized severity (1–5) assigned at ingestion by a per-category policy, combined with each user's thresholds in rules. No editorial curation, no ML/NLP. | The only reading that is testable without a data-science project. | Curated feed: publish/approve workflow. ML scoring: not feasible in 24 h. | PROPOSED |
| A-04 | **Sources.** Push API (admin/system), simulator, and (stretch) a USGS earthquake poller. No licensed news or market feeds. | Free, key-less, stable sources; the same SPI admits more. | Real feeds: +4–6 h each plus licensing. | PROPOSED |
| A-05 | **Immutability.** Events are immutable. A later revision with the same (source, externalId) is ignored: first write wins, no re-notify. | Keeps idempotency simple and auditable. | Revisions (for example magnitude updates) need an `event_revisions` table and a re-notify policy. | PROPOSED |
| A-06 | **Rule semantics.** One category per rule. Criteria are AND-ed: min severity; regions (any-of; `US` also matches `US-CA`); keywords (any-of, whole word or phrase, case-insensitive, over title + summary); attribute conditions (all-of). Optional cooldown. Several categories = several rules. | Simple to explain, index and test. | Multi-category or OR groups need a criteria tree (about +2–3 h). | PROPOSED |
| A-07 | **Channels.** Slack = user-supplied incoming-webhook URL (`https://hooks.slack.com/…`). Email = SMTP to the destination address from one configured sender. | Webhooks need no Slack app, OAuth or public URL. | Slack app or DMs: OAuth install, token storage, scopes (+6–8 h). | PROPOSED |
| A-08 | **Destination ownership is not verified** (no confirmation email, no Slack handshake). | Verification flows are a feature of their own. | Accepted abuse vector (a user can target someone else's address). Mitigated by quotas and the "test send" stretch item. | PROPOSED |
| A-09 | **Admin view** = operations console (R-14 … R-16) plus minimal user list/deactivate. | "Admin view" is the only hint in the brief. | User onboarding, billing or moderation: about +3 h. | PROPOSED |
| A-10 | **Identity is a stub.** `X-User-Id` resolves a seeded user; the role comes from the database; the UI has a "view as" switcher. Ownership and role checks are real and sit behind a `CurrentUser` port. | Real auth is not in the brief and costs hours. | Real auth replaces the stub without touching business code. The stub is not safe for production and is documented as such. | PROPOSED |
| A-11 | **Delivery guarantee.** At-least-once delivery with exactly one notification row per (event, rule, destination). Rare duplicates are possible if a worker dies between provider acceptance and our commit. | Exactly-once is unattainable with SMTP and Slack. | A channel that cannot tolerate duplicates needs provider-side idempotency keys. | PROPOSED |
| A-12 | **Design envelope** (smoke-tested, not benchmarked): ≤ 10,000 users; ≤ 100,000 rules; sustained ≤ 5 events/s with bursts to 50/s; fan-out ≤ 2,000 notifications per event; ≥ 200 deliveries/s per node; first attempt within 10 s at p95. Single node by default, multi-node safe. | No volumes were given; these are plausible for an internal alerting tool. | Higher volumes: see doc 02 §12. | PROPOSED |
| A-13 | **Time, language, retention.** UTC everywhere; English only; no per-user timezone, quiet hours or digests; data kept forever (no purge job). | Not in the brief. | Each is an add-on feature (doc 02 §12). | PROPOSED |
| A-14 | **Stale events.** Events older than 6 h at match time are stored but not notified (configurable). | Prevents an alert storm after a poller restart or backfill. | If the window is too short, legitimate late events are dropped; make it per source. | PROPOSED |
| A-15 | **[APPROVAL] Framework line.** Spring Boot 4.1.x (current stable on spring.io; official docs: Java 17–27, Spring Framework 7) satisfies CLAUDE.md's "3.4+". Fallback: 3.5.x (official docs: Java up to 25). Gradle wrapper ≥ 9.1 so that Gradle itself can run on JDK 25. | 3.4 is well past its minimum support window; 4.1.x is the current line. | Switching later is a mechanical migration; the M1 spike verifies the choice. | PROPOSED |
| A-16 | **[APPROVAL] Extra dependencies** beyond CLAUDE.md's list: Flyway (migrations), Spring Mail, Actuator + Micrometer. Test only: WireMock, GreenMail, Awaitility, ArchUnit; JaCoCo plugin. Possible later: Testcontainers (PostgreSQL), springdoc-openapi, Resilience4j. Doc 03 (A-24) proposes adding the Prometheus registry. | Versioned schema, real adapters and verifiable behavior need them. | Rejecting one removes the matching verification (without GreenMail the Email adapter is untestable offline). | PROPOSED |
| A-17 | **One portable SQL dialect.** One Flyway migration set for H2 (PostgreSQL mode) and PostgreSQL: sequences, CHECK constraints; no partial or expression indexes, no jsonb operators, no SKIP LOCKED; JSON stored as text. | Lets H2 tests mean something for PostgreSQL. | PostgreSQL-only features would speed some queries but split the schema. | PROPOSED |
| A-18 | **[APPROVAL] Frontend delivery.** No Node toolchain or bundler. Vanilla ES modules. Tailwind via a committed, prebuilt CSS file (generated once with the Tailwind CLI) so the UI works offline; the CDN script is the fallback. | CLAUDE.md: vanilla JS + Tailwind served statically, zero friction. | CDN-only saves about 0.5 h but needs internet while reviewing. | PROPOSED |
| A-19 | **Channel modes.** The local profile runs Email and Slack in DRY_RUN (render, log, record as sent; an admin can inject failures for demos). LIVE mode is configuration only (SMTP host, real webhooks). | Reviewers see the full pipeline without credentials. | If you want LIVE by default you must provide an SMTP target and a Slack webhook. | PROPOSED |
| A-20 | **Naming.** Base package `com.example.alerting` and artifact `alerting-service` are placeholders. | Not specified. | Provide the real group and artifact before M1. | PROPOSED |

A-21 … A-26 (security, observability and delivery) are proposed in [doc 03 §7](03-SECURITY-OPERATIONS-DELIVERY.md#7-assumptions-and-questions).

## 11. Open questions for the PM

| ID | Question | Our default | Affects |
|---|---|---|---|
| Q-01 | Are NEWS, MARKET and DISASTER the full initial set of "world events"? | Yes, plus data-driven extension | A-03, schema |
| Q-02 | Do we have licensed or real feeds, or are simulated and public data fine? | Simulated + public | A-04 |
| Q-03 | Who are the users: internal staff, customers, the public? | Internal, trusted | A-08, A-10 |
| Q-04 | Slack: per-user webhook, or an org Slack app posting to channels and DMs? | Per-user webhook | A-07 |
| Q-05 | What must the admin be able to do besides watching and retrying? | As in R-14 … R-16 | A-09 |
| Q-06 | Latency and volume expectations? Any SLA? | As in A-12 | A-12 |
| Q-07 | Alert-fatigue controls: cooldown only, or digests and quiet hours too? | Cooldown only | A-13 |
| Q-08 | Compliance: unsubscribe links, GDPR export/erasure, retention? | Out of scope, flagged | §7 |
| Q-09 | Email: which sender, domain and provider? | Single SMTP configuration | A-07, A-19 |
| Q-10 | Real group/artifact names and repository conventions? | Placeholders | A-20 |

Q-11 … Q-14 (hosting, compliance, existing monitoring, Kubernetes) are in [doc 03 §7](03-SECURITY-OPERATIONS-DELIVERY.md#7-assumptions-and-questions). Answered on 2026-11-10: the MVP runs locally on local infrastructure, with simulated data and no existing monitoring stack (Q-14 is inferred from Q-11).

## 12. Approval checklist for Phase 2

- [ ] Product interpretations A-03 … A-09 (or your edits)
- [ ] Identity stub and security posture A-10
- [ ] Delivery guarantee and design envelope A-11, A-12
- [ ] **[APPROVAL]** Framework line A-15
- [ ] **[APPROVAL]** Extra dependencies A-16
- [ ] **[APPROVAL]** Frontend delivery A-18
- [ ] Milestone plan, budget and cut list (§6)
- [ ] Scope-out list (§7)
- [ ] Verification strategy and demo scenarios (§8)
- [ ] "Go" for M1, and the branch/PR convention in §5

## 13. Change log

| Date | Version | Change |
|---|---|---|
| 2026-11-10 | v0.1 | Initial draft for review |
| 2026-11-10 | v0.2 | Added doc 03 (security, observability, delivery) and pointers to it: scope-out rows, A-16, A-21 … A-26, Q-11 … Q-14. Milestones and budget are unchanged until you choose a package (doc 03 §5). |
| 2026-11-10 | v0.3 | Recorded the answers to Q-11 … Q-13 (Q-14 inferred) and their effect on the plan (doc 03 §5.4). Milestones and budget are still unchanged. |
