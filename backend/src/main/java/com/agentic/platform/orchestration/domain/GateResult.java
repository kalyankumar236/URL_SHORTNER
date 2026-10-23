package com.agentic.platform.orchestration.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Stored outcome of a deterministic gate evaluation, with the evidence used. */
@Entity
@Table(name = "gate_result")
public class GateResult {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "workflow_id", nullable = false, length = 36)
    private String workflowId;

    @Column(name = "gate_name", nullable = false, length = 48)
    private String gateName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GateOutcome outcome;

    @Column(name = "evidence_json", columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected GateResult() {
    }

    public GateResult(String id, String workflowId, String gateName, GateOutcome outcome, String evidenceJson) {
        this.id = id;
        this.workflowId = workflowId;
        this.gateName = gateName;
        this.outcome = outcome;
        this.evidenceJson = evidenceJson;
    }

    public String getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public String getGateName() { return gateName; }
    public GateOutcome getOutcome() { return outcome; }
    public String getEvidenceJson() { return evidenceJson; }
    public Instant getCreatedAt() { return createdAt; }
}
