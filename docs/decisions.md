# Architecture Decision Records

Lightweight ADRs. Each records context, decision, alternatives, and consequences.

## ADR-001 — Single backend module with separated packages
**Context:** Requirements allow either separate Maven modules or separated packages.
Operational simplicity matters for an interview-ready prototype.
**Decision:** One Spring Boot Maven module with `urlshortener` and `orchestration`
packages, enforced by package boundaries and distinct Spring `@Service` ownership.
**Alternatives:** Multi-module Maven reactor (more ceremony); microservices (needless ops).
**Consequences:** Faster build/run; discipline needed to keep domains decoupled. Can be
split later without API changes.

## ADR-002 — H2 default, PostgreSQL via profile
**Context:** Docker is not installed in the target environment; the demo must run.
**Decision:** Default `dev`/`test` profiles use H2 in PostgreSQL-compatibility mode.
`docker`/`prod` profiles use PostgreSQL. Flyway migrations are written in portable SQL.
**Alternatives:** Require Docker+Postgres (not runnable here); SQLite (weaker JPA support).
**Consequences:** Zero-dependency local run. Postgres-specific features avoided in
migrations. Real Postgres still validated via docker-compose when Docker is available.

## ADR-003 — Deterministic orchestration core, LLM as untrusted proposer
**Context:** Safety must not depend on model judgment.
**Decision:** State machine, DAG validation, gates, approvals, retry limits, and policy
are plain Java with unit tests. The LLM only proposes structured content; a gate can
read an LLM recommendation but the pass/fail condition is deterministic code.
**Alternatives:** LLM-driven control flow (unsafe, non-reproducible).
**Consequences:** Reproducible, testable, offline-capable via mock provider.

## ADR-004 — Pluggable LLM provider with deterministic mock default
**Context:** Demo must run without paid LLM access; real reasoning optional.
**Decision:** `LlmProvider` interface with `MockLlmProvider` (default, deterministic,
seeded) and `OpenAiLlmProvider` (activated by `LLM_PROVIDER` + credentials).
**Consequences:** Reproducible tests/scenarios. Mock output is clearly labeled as mock,
never presented as genuine model reasoning.

## ADR-005 — Append-only audit via WorkflowEvent
**Context:** Decision lineage must be tamper-evident at the application layer.
**Decision:** `WorkflowEvent` rows are insert-only (no update/delete paths in code),
ordered by a per-workflow monotonic `seq`. Secrets/PII are redacted before persistence.
**Alternatives:** Mutable status columns only (loses history).
**Consequences:** Full replayable history; storage grows — acceptable for prototype.

## ADR-006 — Async, best-effort analytics
**Context:** Redirects must stay fast; analytics must not block the hot path.
**Decision:** Redirect resolves and responds first; click events are recorded on a
bounded async executor with bounded retries for transient DB failures. Analytics
failure never fails a redirect.
**Consequences:** Eventual consistency for analytics (documented). Brownfield Scenario B
hardens this exact path.

## ADR-007 — In-memory rate limiting default, Redis optional
**Context:** Distributed rate limiting needs Redis; local demo should not require it.
**Decision:** Token-bucket in-memory limiter by default; Redis-backed limiter under the
`docker`/`prod` profile. Explicit fallback policy: fail-open with structured warning log
if the limiter backend is unavailable (configurable).
**Consequences:** Single-node correctness locally; horizontal correctness with Redis.

## ADR-008 — Short code generation
**Context:** Codes must be unique and unpredictable.
**Decision:** 7-char base62 from a `SecureRandom`, uniqueness enforced by a DB unique
constraint; on collision, regenerate with bounded attempts inside a transaction.
Custom aliases validated against an allowlist pattern and reserved-word list.
**Consequences:** ~62^7 space; collision handling is correct under concurrency via the
unique constraint, not just a pre-check.
