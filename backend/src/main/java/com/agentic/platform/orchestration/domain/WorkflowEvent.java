package com.agentic.platform.orchestration.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Append-only audit event. Application code only ever inserts these — never updates
 * or deletes — forming a replayable decision-lineage spine. Ordered by {@code seq}
 * within a workflow.
 */
@Entity
@Table(name = "workflow_event")
public class WorkflowEvent {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "workflow_id", nullable = false, length = 36)
    private String workflowId;

    @Column(nullable = false)
    private long seq;

    @Column(nullable = false, length = 48)
    private String type;

    @Column(name = "task_id", length = 36)
    private String taskId;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(length = 128)
    private String actor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected WorkflowEvent() {
    }

    public WorkflowEvent(String id, String workflowId, long seq, String type,
                         String taskId, String payloadJson, String actor) {
        this.id = id;
        this.workflowId = workflowId;
        this.seq = seq;
        this.type = type;
        this.taskId = taskId;
        this.payloadJson = payloadJson;
        this.actor = actor;
    }

    public String getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public long getSeq() { return seq; }
    public String getType() { return type; }
    public String getTaskId() { return taskId; }
    public String getPayloadJson() { return payloadJson; }
    public String getActor() { return actor; }
    public Instant getCreatedAt() { return createdAt; }
}
