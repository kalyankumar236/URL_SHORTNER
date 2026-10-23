package com.agentic.platform.orchestration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Request to create a workflow. {@code taskInputs} optionally injects per-task input
 * JSON (keyed by task name) — used by scenarios to drive deterministic fault injection,
 * e.g. {@code {"impl-backend":"{\"faultInjection\":{\"transientFailures\":2}}"}}.
 */
public record CreateWorkflowRequest(
        @NotBlank @Size(max = 8000) String requirement,
        Map<String, String> taskInputs
) {
}
