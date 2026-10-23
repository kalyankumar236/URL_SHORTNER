package com.agentic.platform.orchestration.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A single orchestration workflow: the durable root aggregate holding requirement
 * text, status, stage, plan version, and its tasks.
 */
@Entity
@Table(name = "workflow")
public class Workflow {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "original_requirement", nullable = false, columnDefinition = "TEXT")
    private String originalRequirement;

    @Column(name = "normalized_requirement", columnDefinition = "TEXT")
    private String normalizedRequirement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkflowStatus status = WorkflowStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkflowStage stage = WorkflowStage.INTAKE;

    @Column(name = "plan_version", nullable = false)
    private int planVersion = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "created_by", length = 128)
    private String createdBy;

    protected Workflow() {
    }

    public Workflow(String id, String originalRequirement, String createdBy) {
        this.id = id;
        this.originalRequirement = originalRequirement;
        this.createdBy = createdBy;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getOriginalRequirement() { return originalRequirement; }
    public String getNormalizedRequirement() { return normalizedRequirement; }
    public void setNormalizedRequirement(String v) { this.normalizedRequirement = v; }
    public WorkflowStatus getStatus() { return status; }
    public void setStatus(WorkflowStatus status) { this.status = status; }
    public WorkflowStage getStage() { return stage; }
    public void setStage(WorkflowStage stage) { this.stage = stage; }
    public int getPlanVersion() { return planVersion; }
    public void setPlanVersion(int planVersion) { this.planVersion = planVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
}
