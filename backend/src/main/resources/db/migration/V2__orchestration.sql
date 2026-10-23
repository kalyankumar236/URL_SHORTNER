-- Orchestration engine schema. Portable SQL (H2 PostgreSQL mode + PostgreSQL).
-- UUID primary keys stored as VARCHAR(36) for portability.

CREATE TABLE workflow (
    id                      VARCHAR(36)  PRIMARY KEY,
    original_requirement    TEXT         NOT NULL,
    normalized_requirement  TEXT         NULL,
    status                  VARCHAR(32)  NOT NULL,
    stage                   VARCHAR(32)  NOT NULL,
    plan_version            INT          NOT NULL DEFAULT 1,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              VARCHAR(128) NULL
);

CREATE INDEX idx_workflow_status ON workflow (status);

CREATE TABLE task (
    id              VARCHAR(36)  PRIMARY KEY,
    workflow_id     VARCHAR(36)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    agent_role      VARCHAR(48)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    risk            VARCHAR(16)  NOT NULL DEFAULT 'LOW',
    attempts        INT          NOT NULL DEFAULT 0,
    max_attempts    INT          NOT NULL DEFAULT 3,
    plan_version    INT          NOT NULL DEFAULT 1,
    input_ref       TEXT         NULL,
    output_ref      TEXT         NULL,
    error_detail    TEXT         NULL,
    started_at      TIMESTAMP    NULL,
    finished_at     TIMESTAMP    NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_workflow FOREIGN KEY (workflow_id) REFERENCES workflow (id) ON DELETE CASCADE
);

CREATE INDEX idx_task_workflow ON task (workflow_id);
CREATE INDEX idx_task_status ON task (workflow_id, status);

CREATE TABLE task_dependency (
    task_id          VARCHAR(36) NOT NULL,
    depends_on_id    VARCHAR(36) NOT NULL,
    PRIMARY KEY (task_id, depends_on_id),
    CONSTRAINT fk_dep_task FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE,
    CONSTRAINT fk_dep_parent FOREIGN KEY (depends_on_id) REFERENCES task (id) ON DELETE CASCADE
);

-- Append-only audit spine. No UPDATE/DELETE in application code.
CREATE TABLE workflow_event (
    id            VARCHAR(36)  PRIMARY KEY,
    workflow_id   VARCHAR(36)  NOT NULL,
    seq           BIGINT       NOT NULL,
    type          VARCHAR(48)  NOT NULL,
    task_id       VARCHAR(36)  NULL,
    payload_json  TEXT         NULL,
    actor         VARCHAR(128) NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_event_workflow FOREIGN KEY (workflow_id) REFERENCES workflow (id) ON DELETE CASCADE,
    CONSTRAINT uk_event_seq UNIQUE (workflow_id, seq)
);

CREATE INDEX idx_event_workflow_seq ON workflow_event (workflow_id, seq);

CREATE TABLE gate_result (
    id            VARCHAR(36)  PRIMARY KEY,
    workflow_id   VARCHAR(36)  NOT NULL,
    gate_name     VARCHAR(48)  NOT NULL,
    outcome       VARCHAR(16)  NOT NULL,
    evidence_json TEXT         NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gate_workflow FOREIGN KEY (workflow_id) REFERENCES workflow (id) ON DELETE CASCADE
);

CREATE INDEX idx_gate_workflow ON gate_result (workflow_id);

CREATE TABLE approval (
    id              VARCHAR(36)  PRIMARY KEY,
    workflow_id     VARCHAR(36)  NOT NULL,
    task_id         VARCHAR(36)  NULL,
    action_summary  VARCHAR(512) NOT NULL,
    risk            VARCHAR(16)  NOT NULL,
    affected_json   TEXT         NULL,
    requested_by    VARCHAR(128) NULL,
    decision        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    reviewer        VARCHAR(128) NULL,
    reason          VARCHAR(512) NULL,
    requested_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at      TIMESTAMP    NULL,
    CONSTRAINT fk_approval_workflow FOREIGN KEY (workflow_id) REFERENCES workflow (id) ON DELETE CASCADE,
    CONSTRAINT ck_approval_decision CHECK (decision IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_approval_workflow ON approval (workflow_id);
CREATE INDEX idx_approval_decision ON approval (decision);
