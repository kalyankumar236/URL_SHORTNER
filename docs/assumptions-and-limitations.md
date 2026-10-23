# Assumptions and Limitations

This document is kept honest: it states what is implemented, what is assumed, and what
is **not run / not complete** in the current environment. It is updated as behavior
changes.

## Environment limitations (verified 2026-10-21)

| Area | Status | Detail |
|------|--------|--------|
| Docker | ❌ Not installed | `docker-compose.yml` and Testcontainers tests are provided but **cannot be executed here**. They are reported as `NOT RUN`, never as `PASS`. |
| PostgreSQL | ⚠️ Via profile only | Local verification uses H2 (PostgreSQL mode). Real Postgres path exercised only when Docker/Postgres is available. |
| Redis | ⚠️ Via profile only | In-memory rate limiter/cache used locally. |
| Real LLM | ⚠️ Optional | No credentials assumed. All scenarios run in **mock-provider mode**. Model-backed mode activates only with `LLM_PROVIDER` + `LLM_API_KEY`. |

## Explicit assumptions

1. **Auth model (prototype):** A single configurable admin credential (HTTP Basic)
   protects mutating/admin and orchestration endpoints. Public endpoints: create link,
   redirect, read public link metadata. This is a prototype boundary, not a production
   IAM. Documented in the threat model.
2. **Approver identity** is taken from the authenticated principal; an agent principal
   can never be an approver (enforced server-side).
3. **"Destructive" classification** for approvals is driven by a deterministic policy
   table (e.g., disable/delete link, schema-destructive migration, policy change).
4. **Analytics privacy:** raw IPs are never stored; `client_hash` is a salted, truncated
   hash. Referrer stored as host only. User-agent reduced to a coarse class.
5. **Mock LLM output is deterministic and clearly labeled.** It is never presented as
   genuine model reasoning.
6. **Prototype performance targets** (Scenario C) are *proposed* targets, not measured
   production SLOs.

## Known limitations / not-yet-complete

- Testcontainers integration tests require Docker → **NOT RUN** here (provided for CI).
- Rollback covers orchestration-state compensation and repository changes via an
  **isolated workspace/branch + retained diff**; it does **not** promise universal
  rollback of arbitrary external side effects (explicitly out of scope).
- Real OpenAI/Azure adapter is wired and unit-tested against a stubbed HTTP client but
  not executed against a live endpoint in this environment.
- Frontend E2E (Playwright) is scaffolded; the canonical E2E validation script drives
  the backend scenarios headlessly.

## Residual risks

- Single-node rate limiting without Redis is not horizontally correct.
- HTTP Basic admin auth is suitable for a prototype demo only.
- Mock provider cannot surface real model hallucination/safety issues — that is why
  agent output is always treated as untrusted and validated deterministically.
