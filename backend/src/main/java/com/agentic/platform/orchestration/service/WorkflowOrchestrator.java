package com.agentic.platform.orchestration.service;

import com.agentic.platform.common.AppProperties;
import com.agentic.platform.orchestration.domain.Approval;
import com.agentic.platform.orchestration.domain.ApprovalDecision;
import com.agentic.platform.orchestration.domain.GateOutcome;
import com.agentic.platform.orchestration.domain.GateResult;
import com.agentic.platform.orchestration.domain.RiskLevel;
import com.agentic.platform.orchestration.domain.Task;
import com.agentic.platform.orchestration.domain.TaskStatus;
import com.agentic.platform.orchestration.domain.Workflow;
import com.agentic.platform.orchestration.domain.WorkflowStage;
import com.agentic.platform.orchestration.domain.WorkflowStatus;
import com.agentic.platform.orchestration.engine.AuditService;
import com.agentic.platform.orchestration.engine.StateMachine;
import com.agentic.platform.orchestration.gate.GateEvaluator;
import com.agentic.platform.orchestration.repo.ApprovalRepository;
import com.agentic.platform.orchestration.repo.TaskRepository;
import com.agentic.platform.orchestration.repo.WorkflowRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Deterministic scheduler and lifecycle manager. Selects READY tasks (all dependencies
 * SUCCEEDED), runs independent tasks in parallel, enforces synchronization barriers,
 * applies human-approval gating for high-risk tasks, evaluates quality gates, advances
 * stage, and resolves terminal states (COMPLETED / SAFE_STOP). Resumable: state lives in
 * the database, so {@link #run} can be re-invoked after an approval decision.
 */
@Service
public class WorkflowOrchestrator {

    private final WorkflowRepository workflowRepo;
    private final TaskRepository taskRepo;
    private final ApprovalRepository approvalRepo;
    private final StateMachine stateMachine;
    private final AuditService audit;
    private final GateEvaluator gateEvaluator;
    private final ApprovalService approvalService;
    private final TaskExecutionService taskExecutionService;
    private final AppProperties props;
    private final ExecutorService executor;

    /** In-memory per-workflow artifact store (documented limitation: not durable across restarts). */
    private final Map<String, Map<String, Object>> artifactStore = new ConcurrentHashMap<>();

    // Which gate to evaluate after a given task succeeds.
    private static final Map<String, String> TASK_GATE = Map.of(
            "requirement-analysis", "requirement",
            "architecture-design", "design",
            "planning", "planning",
            "impl-backend", "implementation",
            "impl-frontend", "implementation",
            "testing", "testing",
            "security-review", "security",
            "release-readiness", "release");

    // Stage reached once a given task succeeds.
    private static final Map<String, WorkflowStage> TASK_STAGE = Map.of(
            "requirement-analysis", WorkflowStage.NORMALIZED,
            "architecture-design", WorkflowStage.DESIGN,
            "planning", WorkflowStage.PLANNED,
            "impl-backend", WorkflowStage.EXECUTING,
            "impl-frontend", WorkflowStage.EXECUTING,
            "testing", WorkflowStage.TESTING,
            "security-review", WorkflowStage.SECURITY,
            "documentation", WorkflowStage.DOCUMENTATION,
            "release-readiness", WorkflowStage.RELEASE_REVIEW);

    public WorkflowOrchestrator(WorkflowRepository workflowRepo, TaskRepository taskRepo,
                                ApprovalRepository approvalRepo, StateMachine stateMachine, AuditService audit,
                                GateEvaluator gateEvaluator, ApprovalService approvalService,
                                TaskExecutionService taskExecutionService, AppProperties props,
                                @Qualifier("orchestrationExecutor") ExecutorService executor) {
        this.workflowRepo = workflowRepo;
        this.taskRepo = taskRepo;
        this.approvalRepo = approvalRepo;
        this.stateMachine = stateMachine;
        this.audit = audit;
        this.gateEvaluator = gateEvaluator;
        this.approvalService = approvalService;
        this.taskExecutionService = taskExecutionService;
        this.props = props;
        this.executor = executor;
    }

    /** Seed (or retrieve) the shared artifact map, rehydrating nothing beyond what tasks already hold. */
    public Map<String, Object> artifacts(String workflowId) {
        return artifactStore.computeIfAbsent(workflowId, k -> new ConcurrentHashMap<>());
    }

    /**
     * Drive the workflow forward until it completes, fails/safe-stops, or pauses waiting
     * for a human approval. Safe to call repeatedly (idempotent on terminal states).
     */
    public Workflow run(String workflowId) {
        Workflow workflow = workflowRepo.findById(workflowId).orElseThrow();
        if (isTerminal(workflow.getStatus())) {
            return workflow;
        }
        setWorkflowStatus(workflow, WorkflowStatus.RUNNING);
        Map<String, Object> artifacts = artifacts(workflowId);

        boolean gateStop = false;
        while (true) {
            List<Task> tasks = activeTasks(workflow);
            Map<String, Task> byId = new HashMap<>();
            tasks.forEach(t -> byId.put(t.getId(), t));

            // 1. Propagate readiness / blocking.
            updateReadiness(workflow, tasks, byId);

            // 2. Terminal checks.
            if (tasks.stream().allMatch(t -> t.getStatus() == TaskStatus.SUCCEEDED)) {
                completeWorkflow(workflow);
                return workflow;
            }
            boolean anyFailed = tasks.stream().anyMatch(t ->
                    t.getStatus() == TaskStatus.FAILED || t.getStatus() == TaskStatus.CANCELLED);
            if (anyFailed || gateStop) {
                safeStop(workflow, gateStop ? "quality gate did not pass" : "a task failed or was cancelled");
                return workflow;
            }

            // 3. Classify runnable vs approval-gated.
            List<Task> executable = new ArrayList<>();
            boolean pausedForApproval = false;
            for (Task t : tasks) {
                boolean ready = t.getStatus() == TaskStatus.READY;
                boolean awaiting = t.getStatus() == TaskStatus.WAITING_FOR_APPROVAL;
                if (!ready && !awaiting) {
                    continue;
                }
                if (t.getRisk() == RiskLevel.HIGH) {
                    ApprovalDecision decision = approvalDecision(t.getId());
                    switch (decision) {
                        case APPROVED -> executable.add(t);
                        case REJECTED -> {
                            transitionTask(t, TaskStatus.CANCELLED);
                            taskRepo.save(t);
                            audit.append(workflowId, "TASK_CANCELLED", t.getId(),
                                    Map.of("reason", "approval rejected"), "orchestrator");
                        }
                        case PENDING -> {
                            if (ready) {
                                requestApproval(workflow, t);
                            }
                            pausedForApproval = true;
                        }
                    }
                } else if (ready) {
                    executable.add(t);
                }
            }

            // 4. Nothing to execute → pause or stall.
            if (executable.isEmpty()) {
                // A rejection (task cancelled) during classification takes priority over pausing.
                if (tasks.stream().anyMatch(t -> t.getStatus() == TaskStatus.CANCELLED
                        || t.getStatus() == TaskStatus.FAILED)) {
                    safeStop(workflow, "a task was cancelled (approval rejected) or failed");
                    return workflow;
                }
                if (pausedForApproval || tasks.stream().anyMatch(t -> t.getStatus() == TaskStatus.WAITING_FOR_APPROVAL)) {
                    setWorkflowStatus(workflow, WorkflowStatus.WAITING_FOR_APPROVAL);
                    audit.append(workflowId, "WORKFLOW_WAITING_FOR_APPROVAL", null,
                            Map.of("pendingApprovals", approvalService.forWorkflow(workflowId).stream()
                                    .filter(a -> a.getDecision() == ApprovalDecision.PENDING).count()), "orchestrator");
                    return workflow;
                }
                // No progress possible (e.g. everything blocked) → safe stop.
                safeStop(workflow, "no runnable tasks remain (blocked dependencies)");
                return workflow;
            }

            // 5. Execute the wave in parallel (sync barrier via join).
            gateStop = executeWave(workflow, executable, artifacts);
        }
    }

    /** Execute all tasks in the wave concurrently, then evaluate gates. Returns true if a gate stopped progress. */
    private boolean executeWave(Workflow workflow, List<Task> wave, Map<String, Object> artifacts) {
        audit.append(workflow.getId(), "WAVE_STARTED", null,
                Map.of("taskCount", wave.size(),
                        "tasks", wave.stream().map(Task::getName).toList(),
                        "parallel", wave.size() > 1), "orchestrator");

        List<Future<TaskExecutionService.ExecOutcome>> futures = new ArrayList<>();
        for (Task t : wave) {
            final String tid = t.getId();
            futures.add(executor.submit(() ->
                    taskExecutionService.execute(workflow.getId(), tid, artifacts)));
        }
        // Sync barrier: wait for all tasks in the wave.
        for (Future<TaskExecutionService.ExecOutcome> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                audit.append(workflow.getId(), "WAVE_TASK_ERROR", null,
                        Map.of("error", String.valueOf(e.getMessage())), "orchestrator");
            }
        }

        // Evaluate gates + advance stage for tasks that succeeded this wave.
        boolean gateStop = false;
        WorkflowStage furthest = workflow.getStage();
        for (Task t : wave) {
            Task refreshed = taskRepo.findById(t.getId()).orElse(t);
            if (refreshed.getStatus() != TaskStatus.SUCCEEDED) {
                continue;
            }
            WorkflowStage s = TASK_STAGE.get(refreshed.getName());
            if (s != null && s.ordinal() > furthest.ordinal()) {
                furthest = s;
            }
            String gate = TASK_GATE.get(refreshed.getName());
            if (gate != null) {
                GateResult gr = gateEvaluator.evaluate(workflow.getId(), gate, artifacts);
                if (gr.getOutcome() == GateOutcome.FAIL || gr.getOutcome() == GateOutcome.BLOCKED) {
                    gateStop = true;
                }
            }
        }
        if (furthest.ordinal() > workflow.getStage().ordinal()) {
            workflow.setStage(furthest);
            workflowRepo.save(workflow);
        }
        return gateStop;
    }

    // ---- readiness ----

    private void updateReadiness(Workflow workflow, List<Task> tasks, Map<String, Task> byId) {
        for (Task t : tasks) {
            if (t.getStatus() != TaskStatus.PENDING) {
                continue;
            }
            boolean depFailed = t.getDependsOn().stream().anyMatch(d -> {
                Task dep = byId.get(d);
                return dep != null && (dep.getStatus() == TaskStatus.FAILED
                        || dep.getStatus() == TaskStatus.CANCELLED);
            });
            if (depFailed) {
                transitionTask(t, TaskStatus.BLOCKED);
                taskRepo.save(t);
                continue;
            }
            boolean allSucceeded = t.getDependsOn().stream().allMatch(d -> {
                Task dep = byId.get(d);
                return dep != null && dep.getStatus() == TaskStatus.SUCCEEDED;
            });
            if (allSucceeded) {
                transitionTask(t, TaskStatus.READY);
                taskRepo.save(t);
            }
        }
    }

    private List<Task> activeTasks(Workflow workflow) {
        return taskRepo.findByWorkflowId(workflow.getId()).stream()
                .filter(t -> t.getPlanVersion() == workflow.getPlanVersion())
                .filter(t -> t.getStatus() != TaskStatus.STALE)
                .toList();
    }

    private ApprovalDecision approvalDecision(String taskId) {
        List<Approval> approvals = approvalRepo.findAll().stream()
                .filter(a -> taskId.equals(a.getTaskId()))
                .toList();
        if (approvals.stream().anyMatch(a -> a.getDecision() == ApprovalDecision.APPROVED)) {
            return ApprovalDecision.APPROVED;
        }
        if (approvals.stream().anyMatch(a -> a.getDecision() == ApprovalDecision.REJECTED)) {
            return ApprovalDecision.REJECTED;
        }
        return ApprovalDecision.PENDING;
    }

    private void requestApproval(Workflow workflow, Task task) {
        boolean exists = approvalRepo.findByWorkflowId(workflow.getId()).stream()
                .anyMatch(a -> task.getId().equals(a.getTaskId()) && a.getDecision() == ApprovalDecision.PENDING);
        if (!exists) {
            approvalService.request(workflow.getId(), task.getId(),
                    "High-risk task '" + task.getName() + "' (" + task.getAgentRole() + ") requires approval",
                    task.getRisk(), null, "orchestrator");
        }
        transitionTask(task, TaskStatus.WAITING_FOR_APPROVAL);
        taskRepo.save(task);
    }

    // ---- lifecycle helpers ----

    private void completeWorkflow(Workflow workflow) {
        setWorkflowStatus(workflow, WorkflowStatus.COMPLETED);
        workflow.setStage(WorkflowStage.COMPLETED);
        workflowRepo.save(workflow);
        audit.append(workflow.getId(), "WORKFLOW_COMPLETED", null, Map.of(), "orchestrator");
    }

    private void safeStop(Workflow workflow, String reason) {
        setWorkflowStatus(workflow, WorkflowStatus.SAFE_STOP);
        audit.append(workflow.getId(), "WORKFLOW_SAFE_STOP", null, Map.of("reason", reason), "orchestrator");
    }

    private void setWorkflowStatus(Workflow workflow, WorkflowStatus to) {
        if (workflow.getStatus() == to) {
            return;
        }
        stateMachine.assertTransition(workflow.getStatus(), to);
        workflow.setStatus(to);
        workflowRepo.save(workflow);
    }

    private void transitionTask(Task task, TaskStatus to) {
        stateMachine.assertTransition(task.getStatus(), to);
        task.setStatus(to);
    }

    private boolean isTerminal(WorkflowStatus s) {
        return s == WorkflowStatus.COMPLETED || s == WorkflowStatus.FAILED
                || s == WorkflowStatus.CANCELLED || s == WorkflowStatus.SAFE_STOP;
    }

    @Transactional
    public void clearArtifacts(String workflowId) {
        artifactStore.remove(workflowId);
    }
}
