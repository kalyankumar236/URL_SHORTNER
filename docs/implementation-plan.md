# Implementation Plan — Agentic SDLC Platform

This plan is a living document. It is updated after each phase with actual status
and evidence. Status legend: `[ ]` not started · `[~]` in progress · `[x]` done ·
`[!]` blocked/limited (see Assumptions & Limitations).

## Architecture at a glance

Two integrated systems in one monorepo:

1. **URL Shortener** — production-minded Spring Boot service (create/redirect/manage
   links + analytics).
2. **Agentic SDLC Orchestration Engine** — deterministic, stateful workflow engine
   that turns a natural-language requirement into a reviewable engineering outcome
   via coordinated agents, gates, approvals, retries, replanning, policy, and audit.

Backend is a **single Spring Boot Maven module** with clearly separated packages
(`urlshortener`, `orchestration`) — the smallest architecture that satisfies the
requirements. Frontend is a separate Angular app.

## Environment constraints (verified 2026-10-21)

| Tool    | Status | Notes |
|---------|--------|-------|
| Java 21 | ✅     | `C:\Program Files\Java\jdk-21.0.10` |
| Maven   | ✅ 3.9.5 | Must export `JAVA_HOME` to JDK 21 (default picks JDK 8) |
| Node/npm| ✅ 24 / 11.3 | |
| Docker  | ❌     | Not installed → Testcontainers & docker-compose cannot run here |

**Consequence:** the app defaults to **H2 (PostgreSQL compatibility mode)** so it runs
with zero Docker. PostgreSQL + Redis are wired via the `docker`/`prod` profiles and
`docker-compose.yml`. Testcontainers tests are provided but reported as **NOT RUN**
in this environment (honest status, not "passing").

## Prioritized task checklist (with dependencies, acceptance, risk, validation)

### Phase 0 — Foundation
- [x] Inspect repo + toolchain · _acc:_ versions known · _risk:_ L · _val:_ `java -version`, `mvn -version`
- [x] Phase-0 docs (plan, architecture, decisions, assumptions) · _acc:_ files exist · _risk:_ L

### Phase 1 — Backend skeleton & infra
- [x] Maven module, Spring Boot app, profiles (dev/docker/prod) · _dep:_ P0 · _risk:_ M · _val:_ `mvn compile`
- [x] Flyway migrations (portable SQL) · _dep:_ P1 · _acc:_ schema creates on H2 · _val:_ app boot
- [x] Actuator health/readiness, OpenAPI, structured logging, correlation IDs · _val:_ `/actuator/health`

### Phase 2 — URL Shortener
- [x] Entities, repos, DTOs, short-code generator (collision-safe) · _risk:_ M
- [x] Create / redirect / get / list / delete / disable APIs · _dep:_ P1 · _val:_ integration tests
- [x] URL validation (scheme allowlist, length, alias rules), expiration · _risk:_ H (security)
- [x] Analytics capture (async, privacy-conscious) + query endpoints · _risk:_ M
- [x] Rate limiting, idempotency, consistent error contract · _risk:_ M
- [x] Unit + integration tests · _val:_ `mvn test`

### Phase 3 — Orchestration engine (deterministic core)
- [x] Workflow + Task + Event + Approval + Gate entities & state machine · _risk:_ H
- [x] DAG model, cycle detection, missing-dep rejection, scheduler · _risk:_ H · _val:_ unit tests
- [x] Entry/exit gates with stored evidence · _dep:_ state machine
- [x] Append-only audit events + decision lineage · _risk:_ M
- [x] Parallel execution of independent tasks + sync barriers · _val:_ test asserts parallelism

### Phase 4 — Agents, policy, approvals, resilience
- [x] Agent interfaces + 8 role implementations · _risk:_ M
- [x] LLM provider adapter + deterministic mock provider · _risk:_ M
- [x] Deterministic policy layer (deny-by-default tool executor) · _risk:_ H (security)
- [x] Retry (backoff+jitter, retryable classification), safe-stop · _risk:_ M
- [x] Human approval checkpoints (backend-enforced) · _risk:_ H
- [x] Dynamic replanning (stale invalidation, selective rerun) · _risk:_ H
- [x] Reliability metrics with documented formulas · _risk:_ M

### Phase 5 — Frontend
- [x] Angular 20 app, strict TS, router, reactive forms, HttpClient
- [x] Dashboard, intake, normalized view, DAG view, execution timeline
- [x] Approval queue, validation results, release-readiness, URL management
- [x] Frontend unit tests

### Phase 6 — Scenarios
- [x] Greenfield / brownfield / ambiguous fixtures + runnable execution
- [x] Parallel task demo + selective replanning demo

### Phase 7 — CI, security, docs, release
- [x] GitHub Actions CI (build, test, lint, frontend build, scans where available)
- [x] Threat model, testing strategy, demo script, final summary
- [x] End-to-end validation script

## Execution order rationale

Build the **deterministic core first** (state machine, DAG, gates, policy) because
every safety guarantee depends on it. Agents/LLM reasoning sit on top and are treated
as untrusted proposers. Frontend and scenarios consume stable APIs last.

## Milestones

- **M1** Backend compiles & boots on H2 with migrations + health. 
- **M2** URL Shortener APIs green (tests pass). 
- **M3** Orchestration engine executes a persisted workflow with gates + approval. 
- **M4** Three scenarios reproducible in mock mode. 
- **M5** Frontend talks to backend; CI + docs complete.
