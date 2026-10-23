package com.agentic.platform.orchestration.service;

import com.agentic.platform.common.ApiException;
import com.agentic.platform.common.AppProperties;
import com.agentic.platform.orchestration.domain.Task;
import com.agentic.platform.orchestration.domain.TaskStatus;
import com.agentic.platform.orchestration.domain.Workflow;
import com.agentic.platform.orchestration.domain.WorkflowStatus;
import com.agentic.platform.orchestration.engine.DagValidator;
import com.agentic.platform.orchestration.engine.StateMachine;
import com.agentic.platform.orchestration.engine.AuditService;
import com.agentic.platform.orchestration.repo.TaskRepository;
import com.agentic.platform.orchestration.repo.WorkflowRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Workflow lifecycle management: creation (with DAG validation), retrieval, execution
 * delegation, cancellation, and replanning. Deterministic controls (state machine, DAG
 * validator) run here; agent execution is delegated to the orchestrator.
 */
@Service
public class WorkflowService {

    private final WorkflowRepository workflowRepo;
    private final TaskRepository taskRepo;
    private final DagValidator dagValidator;
    private final StateMachine stateMachine;
    private final AuditService audit;
    private final WorkflowOrchestrator orchestrator;
    private final AppProperties props;

    public WorkflowService(WorkflowRepository workflowRepo, TaskRepository taskRepo, DagValidator dagValidator,
                           StateMachine stateMachine, AuditService audit, WorkflowOrchestrator orchestrator,
                           AppProperties props) {
        this.workflowRepo = workflowRepo;
        this.taskRepo = taskRepo;
        this.dagValidator = dagValidator;
        this.stateMachine = stateMachine;
        this.audit = audit;
        this.orchestrator = orchestrator;
        this.props = props;
    }

    @Transactional
    public Workflow create(String requirement, String createdBy, Map<String, String> taskInputs) {
        if (requirement == null || requirement.isBlank()) {
            throw ApiException.badRequest("Requirement text is required");
        }
        String id = UUID.randomUUID().toString();
        Workflow workflow = new Workflow(id, requirement.trim(), createdBy);
        workflowRepo.save(workflow);

        List<Task> tasks = TaskGraphBuilder.build(
                id, 1, props.getOrchestration().getDefaultMaxAttempts(), taskInputs);

        DagValidator.ValidationResult validation = dagValidator.validate(tasks);
        if (!validation.valid()) {
            audit.append(id, "DAG_INVALID", null, Map.of("reason", validation.reason()), createdBy);
            throw ApiException.badRequest("Invalid task graph: " + validation.reason());
        }
        taskRepo.saveAll(tasks);

        audit.append(id, "WORKFLOW_CREATED", null,
                Map.of("taskCount", tasks.size(), "createdBy", createdBy == null ? "anonymous" : createdBy), createdBy);
        audit.append(id, "DAG_VALIDATED", null,
                Map.of("topologicalOrder", validation.topologicalOrder().size(), "acyclic", true), "dag-validator");
        return workflow;
    }

    @Transactional(readOnly = true)
    public Workflow get(String id) {
        return workflowRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Workflow not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Workflow> list(Pageable pageable) {
        return workflowRepo.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Task> tasks(String workflowId) {
        get(workflowId);
        return taskRepo.findByWorkflowId(workflowId);
    }

    /** Drive the workflow forward (synchronous, resumable). */
    public Workflow run(String id) {
        get(id); // existence check
        return orchestrator.run(id);
    }

    @Transactional
    public Workflow cancel(String id, String actor) {
        Workflow workflow = get(id);
        if (isTerminal(workflow.getStatus())) {
            throw ApiException.conflict("Workflow already in terminal state: " + workflow.getStatus());
        }
        stateMachine.assertTransition(workflow.getStatus(), WorkflowStatus.CANCELLED);
        workflow.setStatus(WorkflowStatus.CANCELLED);
        workflowRepo.save(workflow);
        for (Task t : taskRepo.findByWorkflowId(id)) {
            if (isCancellable(t.getStatus())) {
                stateMachine.assertTransition(t.getStatus(), TaskStatus.CANCELLED);
                t.setStatus(TaskStatus.CANCELLED);
                taskRepo.save(t);
            }
        }
        audit.append(id, "WORKFLOW_CANCELLED", null, Map.of("actor", actor == null ? "system" : actor), actor);
        return workflow;
    }

    /**
     * Replan: bump the plan version, mark current non-terminal tasks STALE, and lay down
     * a fresh task graph at the new version. Does not auto-run — caller re-invokes run().
     */
    @Transactional
    public Workflow replan(String id, String actor, Map<String, String> taskInputs) {
        Workflow workflow = get(id);
        if (isTerminal(workflow.getStatus()) && workflow.getStatus() != WorkflowStatus.SAFE_STOP) {
            throw ApiException.conflict("Cannot replan a workflow in state: " + workflow.getStatus());
        }
        int newVersion = workflow.getPlanVersion() + 1;
        for (Task t : taskRepo.findByWorkflowId(id)) {
            if (t.getPlanVersion() < newVersion && isStaleable(t.getStatus())) {
                stateMachine.assertTransition(t.getStatus(), TaskStatus.STALE);
                t.setStatus(TaskStatus.STALE);
                taskRepo.save(t);
            }
        }
        List<Task> tasks = TaskGraphBuilder.build(
                id, newVersion, props.getOrchestration().getDefaultMaxAttempts(), taskInputs);
        DagValidator.ValidationResult validation = dagValidator.validate(tasks);
        if (!validation.valid()) {
            throw ApiException.badRequest("Invalid replanned task graph: " + validation.reason());
        }
        taskRepo.saveAll(tasks);

        workflow.setPlanVersion(newVersion);
        if (workflow.getStatus() == WorkflowStatus.SAFE_STOP) {
            // Reopen a safely-stopped workflow for a fresh plan.
            workflow.setStatus(WorkflowStatus.PENDING);
        }
        workflowRepo.save(workflow);
        orchestrator.clearArtifacts(id);
        audit.append(id, "WORKFLOW_REPLANNED", null,
                Map.of("newPlanVersion", newVersion, "actor", actor == null ? "system" : actor), actor);
        return workflow;
    }

    /**
     * Selective replan triggered by an upstream change at {@code fromTaskName}. Invalidates
     * that task and every task that transitively depends on it, resetting them to PENDING so
     * they re-run, while preserving every unaffected completed task (its result stays valid).
     * Records which tasks were invalidated vs. preserved. Does not auto-run — caller re-invokes
     * {@link #run}. This is the spec's "do not blindly rerun the whole workflow" behaviour.
     */
    @Transactional
    public Workflow replanFromTask(String id, String fromTaskName, String reason, String actor,
                                   Map<String, String> taskInputs) {
        Workflow workflow = get(id);
        if (workflow.getStatus() == WorkflowStatus.RUNNING) {
            throw ApiException.conflict("Cannot replan a RUNNING workflow");
        }
        List<Task> active = taskRepo.findByWorkflowId(id).stream()
                .filter(t -> t.getPlanVersion() == workflow.getPlanVersion())
                .filter(t -> t.getStatus() != TaskStatus.STALE)
                .toList();
        Task from = active.stream()
                .filter(t -> fromTaskName.equals(t.getName()))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("No active task named '" + fromTaskName + "'"));

        // Forward transitive closure: the changed task plus everything that depends on it.
        Set<String> affected = new HashSet<>();
        affected.add(from.getId());
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Task t : active) {
                if (!affected.contains(t.getId())
                        && t.getDependsOn().stream().anyMatch(affected::contains)) {
                    affected.add(t.getId());
                    grew = true;
                }
            }
        }

        List<String> invalidated = new ArrayList<>();
        List<String> preserved = new ArrayList<>();
        for (Task t : active) {
            if (affected.contains(t.getId())) {
                resetTaskToPending(t);
                if (t.getId().equals(from.getId()) && taskInputs != null) {
                    String changed = taskInputs.get(fromTaskName);
                    if (changed != null && !changed.isBlank()) {
                        t.setInputRef(changed);
                        taskRepo.save(t);
                    }
                }
                invalidated.add(t.getName());
            } else if (t.getStatus() == TaskStatus.SUCCEEDED) {
                preserved.add(t.getName());
            }
        }

        // Reopen a terminally-stopped workflow so the invalidated subgraph can run again.
        if (workflow.getStatus() == WorkflowStatus.SAFE_STOP
                || workflow.getStatus() == WorkflowStatus.COMPLETED
                || workflow.getStatus() == WorkflowStatus.CANCELLED) {
            workflow.setStatus(WorkflowStatus.PENDING);
            workflowRepo.save(workflow);
        }

        audit.append(id, "WORKFLOW_REPLANNED", null, Map.of(
                "mode", "selective",
                "fromTask", fromTaskName,
                "invalidated", invalidated,
                "preserved", preserved,
                "reason", reason == null ? "upstream change" : reason,
                "actor", actor == null ? "system" : actor), actor);
        return workflow;
    }

    /** Reset a task to a fresh PENDING state through legal transitions, clearing execution state. */
    private void resetTaskToPending(Task t) {
        if (t.getStatus() != TaskStatus.PENDING) {
            if (t.getStatus() != TaskStatus.STALE) {
                stateMachine.assertTransition(t.getStatus(), TaskStatus.STALE);
                t.setStatus(TaskStatus.STALE);
            }
            stateMachine.assertTransition(TaskStatus.STALE, TaskStatus.PENDING);
            t.setStatus(TaskStatus.PENDING);
        }
        t.setAttempts(0);
        t.setOutputRef(null);
        t.setErrorDetail(null);
        t.setStartedAt(null);
        t.setFinishedAt(null);
        taskRepo.save(t);
    }

    private boolean isTerminal(WorkflowStatus s) {
        return s == WorkflowStatus.COMPLETED || s == WorkflowStatus.FAILED
                || s == WorkflowStatus.CANCELLED || s == WorkflowStatus.SAFE_STOP;
    }

    private boolean isCancellable(TaskStatus s) {
        return s != TaskStatus.SUCCEEDED && s != TaskStatus.CANCELLED
                && s != TaskStatus.ROLLED_BACK && s != TaskStatus.STALE;
    }

    private boolean isStaleable(TaskStatus s) {
        return s == TaskStatus.PENDING || s == TaskStatus.READY || s == TaskStatus.BLOCKED
                || s == TaskStatus.SUCCEEDED || s == TaskStatus.FAILED;
    }
}
