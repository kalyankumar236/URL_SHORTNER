package com.agentic.platform.orchestration.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A unit of work owned by one agent role. Dependencies are the ids of tasks that
 * must SUCCEED before this task becomes READY. Stored in {@code task_dependency}.
 */
@Entity
@Table(name = "task")
public class Task {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "workflow_id", nullable = false, length = 36)
    private String workflowId;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_role", nullable = false, length = 48)
    private AgentRole agentRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status = TaskStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RiskLevel risk = RiskLevel.LOW;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "plan_version", nullable = false)
    private int planVersion = 1;

    @Column(name = "input_ref", columnDefinition = "TEXT")
    private String inputRef;

    @Column(name = "output_ref", columnDefinition = "TEXT")
    private String outputRef;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "task_dependency",
            joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "depends_on_id", length = 36)
    private Set<String> dependsOn = new HashSet<>();

    protected Task() {
    }

    public Task(String id, String workflowId, String name, AgentRole agentRole,
                RiskLevel risk, int maxAttempts, int planVersion) {
        this.id = id;
        this.workflowId = workflowId;
        this.name = name;
        this.agentRole = agentRole;
        this.risk = risk;
        this.maxAttempts = maxAttempts;
        this.planVersion = planVersion;
    }

    public String getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public String getName() { return name; }
    public AgentRole getAgentRole() { return agentRole; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public RiskLevel getRisk() { return risk; }
    public void setRisk(RiskLevel risk) { this.risk = risk; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public void incrementAttempts() { this.attempts++; }
    public int getMaxAttempts() { return maxAttempts; }
    public int getPlanVersion() { return planVersion; }
    public void setPlanVersion(int planVersion) { this.planVersion = planVersion; }
    public String getInputRef() { return inputRef; }
    public void setInputRef(String inputRef) { this.inputRef = inputRef; }
    public String getOutputRef() { return outputRef; }
    public void setOutputRef(String outputRef) { this.outputRef = outputRef; }
    public String getErrorDetail() { return errorDetail; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Set<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(Set<String> dependsOn) { this.dependsOn = dependsOn; }
}
