package com.agentic.platform.orchestration.dto;

import com.agentic.platform.orchestration.domain.Approval;

import java.time.Instant;

/** Public view of a human-approval checkpoint. */
public record ApprovalResponse(
        String id,
        String workflowId,
        String taskId,
        String actionSummary,
        String risk,
        String decision,
        String requestedBy,
        String reviewer,
        String reason,
        Instant requestedAt,
        Instant decidedAt
) {
    public static ApprovalResponse from(Approval a) {
        return new ApprovalResponse(
                a.getId(), a.getWorkflowId(), a.getTaskId(), a.getActionSummary(), a.getRisk().name(),
                a.getDecision().name(), a.getRequestedBy(), a.getReviewer(), a.getReason(),
                a.getRequestedAt(), a.getDecidedAt());
    }
}
