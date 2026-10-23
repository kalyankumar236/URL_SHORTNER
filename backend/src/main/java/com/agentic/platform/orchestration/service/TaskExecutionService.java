package com.agentic.platform.orchestration.service;

import com.agentic.platform.orchestration.agent.Agent;
import com.agentic.platform.orchestration.agent.AgentContext;
import com.agentic.platform.orchestration.agent.AgentResult;
import com.agentic.platform.orchestration.domain.AgentRole;
import com.agentic.platform.orchestration.domain.Task;
import com.agentic.platform.orchestration.domain.TaskStatus;
import com.agentic.platform.orchestration.domain.Workflow;
import com.agentic.platform.orchestration.engine.AuditService;
import com.agentic.platform.orchestration.engine.RetryPolicy;
import com.agentic.platform.orchestration.engine.StateMachine;
import com.agentic.platform.orchestration.provider.LlmProvider;
import com.agentic.platform.orchestration.repo.TaskRepository;
import com.agentic.platform.orchestration.repo.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Executes a single task in its own transaction, with deterministic retry handling.
 *
 * <p>This is the only place an (untrusted) agent actually runs. Everything around it —
 * readiness, approvals, gates, scheduling — is deterministic control code. Transient
 * failures are retried within the task's attempt budget; any other failure is terminal.
 */
@Service
public class TaskExecutionService {

    public enum ExecOutcome { SUCCEEDED, FAILED }

    private final WorkflowRepository workflowRepo;
    private final TaskRepository taskRepo;
    private final StateMachine stateMachine;
    private final RetryPolicy retryPolicy;
    private final AuditService audit;
    private final LlmProvider llmProvider;
    private final ObjectMapper mapper;
    private final Map<AgentRole, Agent> agentsByRole = new EnumMap<>(AgentRole.class);

    public TaskExecutionService(WorkflowRepository workflowRepo, TaskRepository taskRepo,
                                StateMachine stateMachine, RetryPolicy retryPolicy, AuditService audit,
                                LlmProvider llmProvider, ObjectMapper mapper, java.util.List<Agent> agents) {
        this.workflowRepo = workflowRepo;
        this.taskRepo = taskRepo;
        this.stateMachine = stateMachine;
        this.retryPolicy = retryPolicy;
        this.audit = audit;
        this.llmProvider = llmProvider;
        this.mapper = mapper;
        for (Agent a : agents) {
            agentsByRole.put(a.role(), a);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExecOutcome execute(String workflowId, String taskId, Map<String, Object> artifacts) {
        Workflow workflow = workflowRepo.findById(workflowId).orElseThrow();
        Task task = taskRepo.findById(taskId).orElseThrow();
        Agent agent = agentsByRole.get(task.getAgentRole());
        if (agent == null) {
            return terminalFail(workflow, task, "No agent registered for role " + task.getAgentRole());
        }

        AgentContext ctx = new AgentContext(workflow, task, llmProvider, artifacts);
        task.setStartedAt(Instant.now());

        while (true) {
            task.incrementAttempts();
            transitionTask(task, TaskStatus.RUNNING);
            taskRepo.save(task);
            audit.append(workflowId, "TASK_STARTED", taskId,
                    Map.of("role", task.getAgentRole().name(), "attempt", task.getAttempts()), "orchestrator");

            try {
                AgentResult result = agent.execute(ctx);

                if (result.getStatus() == AgentResult.Status.FAILED) {
                    return terminalFail(workflow, task, nvl(result.getErrorDetail(), "agent reported failure"));
                }
                if (result.hasBlockingFindings()) {
                    return terminalFail(workflow, task, "blocking findings present");
                }

                // Success: persist outputs and merge into shared artifacts.
                Map<String, Object> outputs = result.getOutputs();
                if (outputs != null) {
                    artifacts.putAll(outputs);
                }
                task.setOutputRef(serializeOutput(result));
                task.setErrorDetail(null);
                task.setFinishedAt(Instant.now());
                transitionTask(task, TaskStatus.SUCCEEDED);
                taskRepo.save(task);
                audit.append(workflowId, "TASK_SUCCEEDED", taskId,
                        Map.of("role", task.getAgentRole().name(), "attempts", task.getAttempts(),
                                "provider", nvl(result.getProviderName(), "n/a"),
                                "mock", result.isMockProvider(),
                                "confidence", result.getConfidence() == null ? "n/a" : result.getConfidence()),
                        "orchestrator");
                return ExecOutcome.SUCCEEDED;

            } catch (Exception ex) {
                boolean canRetry = retryPolicy.canRetry(task.getAttempts(), task.getMaxAttempts(), ex);
                if (canRetry) {
                    long backoff = retryPolicy.backoffMillis(task.getAttempts());
                    transitionTask(task, TaskStatus.RETRYING);
                    task.setErrorDetail(ex.getMessage());
                    taskRepo.save(task);
                    audit.append(workflowId, "TASK_RETRYING", taskId,
                            Map.of("attempt", task.getAttempts(), "maxAttempts", task.getMaxAttempts(),
                                    "backoffMillis", backoff, "error", nvl(ex.getMessage(), "transient")),
                            "orchestrator");
                    // Loop again (no real sleep — backoff is recorded for audit/metrics).
                } else {
                    String reason = retryPolicy.isRetryable(ex)
                            ? "retry budget exhausted after " + task.getAttempts() + " attempts: " + ex.getMessage()
                            : "non-retryable failure: " + ex.getMessage();
                    return terminalFail(workflow, task, reason);
                }
            }
        }
    }

    private ExecOutcome terminalFail(Workflow workflow, Task task, String reason) {
        task.setErrorDetail(reason);
        task.setFinishedAt(Instant.now());
        transitionTask(task, TaskStatus.FAILED);
        taskRepo.save(task);
        audit.append(workflow.getId(), "TASK_FAILED", task.getId(),
                Map.of("role", task.getAgentRole().name(), "attempts", task.getAttempts(), "reason", reason),
                "orchestrator");
        return ExecOutcome.FAILED;
    }

    private void transitionTask(Task task, TaskStatus to) {
        stateMachine.assertTransition(task.getStatus(), to);
        task.setStatus(to);
    }

    private String serializeOutput(AgentResult result) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("outputs", result.getOutputs());
            payload.put("findings", result.getFindings());
            payload.put("confidence", result.getConfidence());
            payload.put("provider", result.getProviderName());
            payload.put("model", result.getModelId());
            payload.put("mock", result.isMockProvider());
            return mapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String nvl(String s, String fallback) {
        return s == null ? fallback : s;
    }
}
