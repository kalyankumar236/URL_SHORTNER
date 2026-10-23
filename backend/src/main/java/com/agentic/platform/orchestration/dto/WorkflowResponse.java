package com.agentic.platform.orchestration.dto;

import com.agentic.platform.orchestration.domain.Workflow;

import java.time.Instant;

/** Public view of a workflow. Never exposes internal entity relationships directly. */
public record WorkflowResponse(
        String id,
        String status,
        String stage,
        int planVersion,
        String originalRequirement,
        String normalizedRequirement,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkflowResponse from(Workflow w) {
        return new WorkflowResponse(
                w.getId(),
                w.getStatus().name(),
                w.getStage().name(),
                w.getPlanVersion(),
                w.getOriginalRequirement(),
                w.getNormalizedRequirement(),
                w.getCreatedBy(),
                w.getCreatedAt(),
                w.getUpdatedAt());
    }
}
