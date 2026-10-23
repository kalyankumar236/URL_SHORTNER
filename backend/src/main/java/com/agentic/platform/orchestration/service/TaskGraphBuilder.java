package com.agentic.platform.orchestration.service;

import com.agentic.platform.orchestration.domain.AgentRole;
import com.agentic.platform.orchestration.domain.RiskLevel;
import com.agentic.platform.orchestration.domain.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the standard SDLC task DAG for a workflow. The graph deliberately contains
 * both parallel branches and synchronization barriers so the scheduler's concurrency
 * and join behaviour can be demonstrated:
 *
 * <pre>
 *   requirement
 *        -> architecture
 *             -> planning
 *                  -> impl-backend  \
 *                  -> impl-frontend  >  (parallel)
 *                         (both) -> testing          (sync barrier)
 *                                      -> security    \
 *                                      -> documentation >  (parallel)
 *                                           (both) -> release-readiness  (sync barrier)
 * </pre>
 *
 * Implementation and release tasks are HIGH risk and therefore require human approval,
 * enforced independently by the engine.
 */
public final class TaskGraphBuilder {

    private TaskGraphBuilder() {
    }

    /**
     * @param workflowId    owning workflow
     * @param planVersion   current plan version (replans increment this)
     * @param maxAttempts   default retry budget per task
     * @param taskInputs    optional per-logical-name input JSON (e.g. fault injection), by task key
     */
    public static List<Task> build(String workflowId, int planVersion, int maxAttempts,
                                   Map<String, String> taskInputs) {
        List<Task> tasks = new ArrayList<>();

        Task requirement = task(workflowId, "requirement-analysis", AgentRole.REQUIREMENT_ANALYST,
                RiskLevel.LOW, maxAttempts, planVersion);
        Task architecture = task(workflowId, "architecture-design", AgentRole.ARCHITECT,
                RiskLevel.LOW, maxAttempts, planVersion);
        Task planning = task(workflowId, "planning", AgentRole.PLANNING,
                RiskLevel.LOW, maxAttempts, planVersion);
        Task implBackend = task(workflowId, "impl-backend", AgentRole.IMPLEMENTATION,
                RiskLevel.HIGH, maxAttempts, planVersion);
        Task implFrontend = task(workflowId, "impl-frontend", AgentRole.IMPLEMENTATION,
                RiskLevel.HIGH, maxAttempts, planVersion);
        Task testing = task(workflowId, "testing", AgentRole.TEST,
                RiskLevel.MEDIUM, maxAttempts, planVersion);
        Task security = task(workflowId, "security-review", AgentRole.SECURITY,
                RiskLevel.MEDIUM, maxAttempts, planVersion);
        Task documentation = task(workflowId, "documentation", AgentRole.DOCUMENTATION,
                RiskLevel.LOW, maxAttempts, planVersion);
        Task release = task(workflowId, "release-readiness", AgentRole.RELEASE_READINESS,
                RiskLevel.HIGH, maxAttempts, planVersion);

        architecture.setDependsOn(Set.of(requirement.getId()));
        planning.setDependsOn(Set.of(architecture.getId()));
        implBackend.setDependsOn(Set.of(planning.getId()));
        implFrontend.setDependsOn(Set.of(planning.getId()));
        testing.setDependsOn(Set.of(implBackend.getId(), implFrontend.getId()));
        security.setDependsOn(Set.of(testing.getId()));
        documentation.setDependsOn(Set.of(testing.getId()));
        release.setDependsOn(Set.of(security.getId(), documentation.getId()));

        if (taskInputs != null) {
            applyInput(requirement, taskInputs.get("requirement-analysis"));
            applyInput(architecture, taskInputs.get("architecture-design"));
            applyInput(planning, taskInputs.get("planning"));
            applyInput(implBackend, taskInputs.get("impl-backend"));
            applyInput(implFrontend, taskInputs.get("impl-frontend"));
            applyInput(testing, taskInputs.get("testing"));
            applyInput(security, taskInputs.get("security-review"));
            applyInput(documentation, taskInputs.get("documentation"));
            applyInput(release, taskInputs.get("release-readiness"));
        }

        tasks.add(requirement);
        tasks.add(architecture);
        tasks.add(planning);
        tasks.add(implBackend);
        tasks.add(implFrontend);
        tasks.add(testing);
        tasks.add(security);
        tasks.add(documentation);
        tasks.add(release);
        return tasks;
    }

    private static Task task(String workflowId, String name, AgentRole role, RiskLevel risk,
                             int maxAttempts, int planVersion) {
        return new Task(UUID.randomUUID().toString(), workflowId, name, role, risk, maxAttempts, planVersion);
    }

    private static void applyInput(Task task, String inputJson) {
        if (inputJson != null && !inputJson.isBlank()) {
            task.setInputRef(inputJson);
        }
    }
}
