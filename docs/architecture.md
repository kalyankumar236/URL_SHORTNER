# Architecture — Agentic SDLC Platform

## 1. System context

```mermaid
graph TB
    User[User / Reviewer] -->|HTTP| FE[Angular 20 Frontend]
    FE -->|REST /api/v1| BE[Spring Boot Backend]
    subgraph BE[Spring Boot Backend - single module]
        US[URL Shortener package]
        ORCH[Orchestration Engine package]
        SEC[Security / Policy layer]
    end
    BE -->|JPA / Flyway| DB[(PostgreSQL / H2)]
    BE -->|cache + rate limit| REDIS[(Redis - optional)]
    ORCH -->|provider adapter| LLM{{LLM Provider}}
    LLM -.mock.-> MOCK[Deterministic Mock Provider]
    LLM -.optional.-> REAL[OpenAI / Azure OpenAI]
```

The backend is one deployable unit with two cohesive domains. H2 is the default
datastore (zero-Docker local dev); PostgreSQL + Redis activate under the `docker`/`prod`
Spring profiles.

## 2. Backend package structure

```
com.agentic.platform
├── common            # error contract, correlation-id filter, pagination, config
├── security          # HTTP security, admin auth, secure headers, CORS
├── urlshortener
│   ├── api           # controllers (links, analytics, redirect)
│   ├── dto           # request/response DTOs (never expose entities)
│   ├── domain        # Link, ClickEvent entities + enums
│   ├── repo          # Spring Data JPA repositories
│   └── service       # code generation, validation, analytics, rate limit
└── orchestration
    ├── api           # workflow, approval, metrics, agents, policies controllers
    ├── dto           # orchestration DTOs
    ├── domain        # Workflow, Task, WorkflowEvent, Approval, Gate entities + enums
    ├── repo          # repositories
    ├── engine        # state machine, DAG, scheduler, gate evaluator
    ├── agent         # agent interfaces + 8 role implementations
    ├── provider      # LlmProvider adapter + MockLlmProvider + OpenAiLlmProvider
    ├── policy        # deterministic policy layer + controlled tool executor
    └── metrics       # reliability metric computation
```

## 3. URL Shortener data model

```mermaid
erDiagram
    LINK ||--o{ CLICK_EVENT : records
    LINK {
        bigint id PK
        varchar short_code UK
        varchar destination_url
        varchar status
        timestamptz expires_at
        timestamptz created_at
        varchar created_by
    }
    CLICK_EVENT {
        bigint id PK
        bigint link_id FK
        timestamptz occurred_at
        int response_status
        varchar client_hash
        varchar referrer_host
        varchar ua_class
    }
```

Indexes: unique on `short_code`; btree on `click_event(link_id, occurred_at)` for
range/daily aggregation; `link(status, expires_at)` for expiry sweeps. Raw IP is
never stored — `client_hash` is a salted, truncated hash (privacy-conscious).

## 4. Orchestration data model

```mermaid
erDiagram
    WORKFLOW ||--o{ TASK : contains
    WORKFLOW ||--o{ WORKFLOW_EVENT : logs
    WORKFLOW ||--o{ APPROVAL : gates
    WORKFLOW ||--o{ GATE_RESULT : evaluates
    TASK ||--o{ TASK_DEPENDENCY : depends_on
    WORKFLOW {
        uuid id PK
        text original_requirement
        text normalized_requirement
        varchar status
        varchar stage
        int plan_version
        timestamptz created_at
    }
    TASK {
        uuid id PK
        uuid workflow_id FK
        varchar name
        varchar agent_role
        varchar status
        varchar risk
        int attempts
        int max_attempts
        int plan_version
    }
    WORKFLOW_EVENT {
        uuid id PK
        uuid workflow_id FK
        bigint seq
        varchar type
        text payload_json
        timestamptz created_at
    }
    APPROVAL {
        uuid id PK
        uuid workflow_id FK
        varchar action_summary
        varchar risk
        varchar decision
        varchar reviewer
        timestamptz decided_at
    }
```

`WORKFLOW_EVENT` is append-only (no update/delete in application code) — the audit
spine. `seq` is a per-workflow monotonic counter for deterministic ordering.

## 5. Orchestration control flow

```mermaid
stateDiagram-v2
    [*] --> INTAKE
    INTAKE --> NORMALIZED: Requirement Analyst
    NORMALIZED --> AMBIGUITY_ASSESSED
    AMBIGUITY_ASSESSED --> DESIGN: requirement gate PASS
    DESIGN --> PLANNED: design gate PASS
    PLANNED --> DAG_VALIDATED: planning gate PASS (acyclic)
    DAG_VALIDATED --> EXECUTING
    EXECUTING --> TESTING
    TESTING --> SECURITY: testing gate PASS
    SECURITY --> DOCUMENTATION: security gate PASS
    DOCUMENTATION --> RELEASE_REVIEW
    RELEASE_REVIEW --> WAITING_FOR_APPROVAL: release gate needs human
    WAITING_FOR_APPROVAL --> COMPLETED: approved
    WAITING_FOR_APPROVAL --> SAFE_STOP: rejected
    EXECUTING --> SAFE_STOP: policy violation / retry exhausted
    EXECUTING --> CANCELLED: cancel request
```

## 6. Task scheduling (DAG)

The scheduler selects tasks whose dependencies have all `SUCCEEDED`, runs independent
tasks concurrently on a bounded thread pool, and enforces a synchronization barrier
before dependent tasks start. Illegal state transitions are rejected by a central
`StateMachine`. Upstream failure propagates `BLOCKED` to descendants.

```mermaid
graph LR
    A[Analyze Req] --> D[Design]
    D --> P[Plan]
    P --> FE[FE Contract Task]
    P --> BE[BE Schema/API Task]
    FE --> INT[Integration]
    BE --> INT
    INT --> SEC[Security Review]
    SEC --> DOC[Docs]
    DOC --> REL[Release Readiness]
```

`FE Contract` and `BE Schema/API` are independent and execute in parallel after the
planning gate; `Integration` is a barrier that waits for both.

## 7. Trust boundaries

- The **LLM is untrusted**: its output is parsed into structured proposals, never
  executed as instructions. Tool calls route through a deny-by-default executor that
  validates workspace boundaries, command allowlist, timeouts, and output limits.
- **Deterministic code enforces** gates, permissions, approvals, retry limits, and
  state transitions. The LLM may _recommend_ a gate outcome but cannot override it.
- **Humans own** high-impact approvals and final acceptance.

See [security-and-threat-model.md](security-and-threat-model.md) for details.
