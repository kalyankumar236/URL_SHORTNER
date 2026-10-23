package com.agentic.platform.orchestration.dto;

import com.agentic.platform.orchestration.domain.WorkflowEvent;

import java.time.Instant;

/** Public view of an append-only audit event (workflow timeline entry). */
public record WorkflowEventResponse(
        long seq,
        String type,
        String taskId,
        String payloadJson,
        String actor,
        Instant createdAt
) {
    public static WorkflowEventResponse from(WorkflowEvent e) {
        return new WorkflowEventResponse(
                e.getSeq(), e.getType(), e.getTaskId(), e.getPayloadJson(), e.getActor(), e.getCreatedAt());
    }
}
