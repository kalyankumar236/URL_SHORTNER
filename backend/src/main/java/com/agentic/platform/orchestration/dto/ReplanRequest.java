package com.agentic.platform.orchestration.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Request to replan a workflow.
 *
 * <ul>
 *   <li>If {@code fromTask} is provided, a <b>selective</b> replan is performed: the named
 *       task and all of its transitive descendants are invalidated and re-run, while every
 *       unaffected completed task is preserved. This is the spec-mandated behaviour for an
 *       upstream requirement / design-decision / contract change.</li>
 *   <li>If {@code fromTask} is omitted, a <b>full</b> replan lays down a fresh task graph at a
 *       new plan version (used when the whole plan is superseded).</li>
 * </ul>
 *
 * {@code taskInputs} optionally carries changed per-task input JSON (keyed by task name),
 * e.g. a revised assumption or fault-injection override applied to the changed task.
 */
public record ReplanRequest(
        @Size(max = 128) String fromTask,
        @Size(max = 2000) String reason,
        Map<String, String> taskInputs
) {
}
