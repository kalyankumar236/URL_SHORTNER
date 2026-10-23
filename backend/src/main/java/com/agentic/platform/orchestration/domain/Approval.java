package com.agentic.platform.orchestration.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A persisted human-approval checkpoint. The workflow pauses in
 * WAITING_FOR_APPROVAL until an authorized human (never an agent) decides.
 */
@Entity
@Table(name = "approval")
public class Approval {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "workflow_id", nullable = false, length = 36)
    private String workflowId;

    @Column(name = "task_id", length = 36)
    private String taskId;

    @Column(name = "action_summary", nullable = false, length = 512)
    private String actionSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RiskLevel risk;

    @Column(name = "affected_json", columnDefinition = "TEXT")
    private String affectedJson;

    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApprovalDecision decision = ApprovalDecision.PENDING;

    @Column(length = 128)
    private String reviewer;

    @Column(length = 512)
    private String reason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected Approval() {
    }

    public Approval(String id, String workflowId, String taskId, String actionSummary,
                    RiskLevel risk, String affectedJson, String requestedBy) {
        this.id = id;
        this.workflowId = workflowId;
        this.taskId = taskId;
        this.actionSummary = actionSummary;
        this.risk = risk;
        this.affectedJson = affectedJson;
        this.requestedBy = requestedBy;
    }

    public String getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public String getTaskId() { return taskId; }
    public String getActionSummary() { return actionSummary; }
    public RiskLevel getRisk() { return risk; }
    public String getAffectedJson() { return affectedJson; }
    public String getRequestedBy() { return requestedBy; }
    public ApprovalDecision getDecision() { return decision; }
    public void setDecision(ApprovalDecision decision) { this.decision = decision; }
    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
