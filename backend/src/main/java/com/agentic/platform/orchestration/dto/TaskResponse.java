package com.agentic.platform.orchestration.dto;

import com.agentic.platform.orchestration.domain.Task;

import java.time.Instant;
import java.util.List;

/** Public view of a task, including its dependency ids for graph rendering. */
public record TaskResponse(
        String id,
        String name,
        String agentRole,
        String status,
        String risk,
        int attempts,
        int maxAttempts,
        int planVersion,
        List<String> dependsOn,
        String errorDetail,
        Instant startedAt,
        Instant finishedAt
) {
    public static TaskResponse from(Task t) {
        return new TaskResponse(
                t.getId(),
                t.getName(),
                t.getAgentRole().name(),
                t.getStatus().name(),
                t.getRisk().name(),
                t.getAttempts(),
                t.getMaxAttempts(),
                t.getPlanVersion(),
                List.copyOf(t.getDependsOn()),
                t.getErrorDetail(),
                t.getStartedAt(),
                t.getFinishedAt());
    }
}
