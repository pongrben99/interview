# Project Directives & Engineering Standards

## Context
We are building an Event Alerting & Notification System from an intentionally vague PM brief. 
Process transparency, architectural rigor, and verifiable testing are mandatory.

## Tech Stack
- Language: Java 25
- Framework: Spring Boot 3.4+ (Spring Web, Spring Data JPA, Validation)
- Database: PostgreSQL / H2 (in-memory for local plug-and-play reproducibility)
- Build Tool: Gradle (Kotlin or Groovy DSL)
- Frontend (Admin & User View): Lightweight single-page dashboard (Vanilla JS + Tailwind CSS served statically by Spring Boot for zero-friction evaluation)

## Agent Operating Rules
1. NEVER jump straight into writing production code unless explicitly instructed to execute a phase.
2. Keep changes small, modular, and tied to specific milestones.
3. Every architectural assumption must be explicitly stated so the human engineer can challenge or approve it.
4. Prioritize enterprise resilience: idempotency, extensible notification channel strategies, retry/failure tracking, and clean separation of concerns.