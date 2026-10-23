package com.agentic.platform.orchestration.service;

import com.agentic.platform.orchestration.domain.ApprovalDecision;
import com.agentic.platform.orchestration.domain.Task;
import com.agentic.platform.orchestration.domain.TaskStatus;
import com.agentic.platform.orchestration.domain.WorkflowEvent;
import com.agentic.platform.orchestration.domain.WorkflowStatus;
import com.agentic.platform.orchestration.repo.ApprovalRepository;
import com.agentic.platform.orchestration.repo.TaskRepository;
import com.agentic.platform.orchestration.repo.WorkflowEventRepository;
import com.agentic.platform.orchestration.repo.WorkflowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes orchestration metrics from persisted state. Every metric is derived from
 * durable data (workflows, tasks, events, approvals) with documented formulas, so the
 * numbers are reproducible and honest — nothing is fabricated.
 */
@Service
public class MetricsService {

    private final WorkflowRepository workflowRepo;
    private final TaskRepository taskRepo;
    private final WorkflowEventRepository eventRepo;
    private final ApprovalRepository approvalRepo;

    public MetricsService(WorkflowRepository workflowRepo, TaskRepository taskRepo,
                          WorkflowEventRepository eventRepo, ApprovalRepository approvalRepo) {
        this.workflowRepo = workflowRepo;
        this.taskRepo = taskRepo;
        this.eventRepo = eventRepo;
        this.approvalRepo = approvalRepo;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();

        // ---- Workflow status distribution ----
        Map<String, Long> wfByStatus = new LinkedHashMap<>();
        for (WorkflowStatus s : WorkflowStatus.values()) {
            wfByStatus.put(s.name(), workflowRepo.countByStatus(s));
        }
        long totalWorkflows = workflowRepo.count();
        m.put("workflows", Map.of(
                "total", totalWorkflows,
                "byStatus", wfByStatus,
                // completionRate = COMPLETED / total (excludes nothing; honest denominator)
                "completionRate", ratio(wfByStatus.get("COMPLETED"), totalWorkflows),
                "safeStops", wfByStatus.get("SAFE_STOP")));

        // ---- Task status distribution + success rate ----
        List<Task> allTasks = taskRepo.findAll();
        Map<String, Long> taskByStatus = new LinkedHashMap<>();
        for (TaskStatus s : TaskStatus.values()) {
            taskByStatus.put(s.name(), allTasks.stream().filter(t -> t.getStatus() == s).count());
        }
        long succeeded = taskByStatus.get("SUCCEEDED");
        long failed = taskByStatus.get("FAILED");
        long rolledBack = taskByStatus.get("ROLLED_BACK");

        // totalRetries = sum over tasks of max(0, attempts-1); first attempt is not a retry.
        long totalRetries = allTasks.stream().mapToLong(t -> Math.max(0, t.getAttempts() - 1)).sum();
        long tasksThatRetried = allTasks.stream().filter(t -> t.getAttempts() > 1).count();

        m.put("tasks", Map.of(
                "total", allTasks.size(),
                "byStatus", taskByStatus,
                // taskSuccessRate = SUCCEEDED / (SUCCEEDED + FAILED); excludes not-yet-run tasks
                "successRate", ratio(succeeded, succeeded + failed),
                "totalRetries", totalRetries,
                "tasksThatRetried", tasksThatRetried,
                "rollbacks", rolledBack));

        // ---- Latency (mean task execution duration where both timestamps exist) ----
        List<Task> timed = allTasks.stream()
                .filter(t -> t.getStartedAt() != null && t.getFinishedAt() != null)
                .toList();
        double avgTaskMillis = timed.isEmpty() ? 0.0 : timed.stream()
                .mapToLong(t -> Duration.between(t.getStartedAt(), t.getFinishedAt()).toMillis())
                .average().orElse(0.0);

        // Mean workflow duration for COMPLETED workflows (updatedAt - createdAt) as an MTTR proxy.
        double avgWorkflowMillis = workflowRepo.findByStatus(WorkflowStatus.COMPLETED).stream()
                .mapToLong(w -> Duration.between(w.getCreatedAt(), w.getUpdatedAt()).toMillis())
                .average().orElse(0.0);

        m.put("latency", Map.of(
                "avgTaskMillis", round(avgTaskMillis),
                "avgCompletedWorkflowMillis", round(avgWorkflowMillis),
                "sampledTasks", timed.size(),
                "note", "Durations are wall-clock; MTTR proxy = mean COMPLETED workflow lifetime"));

        // ---- Parallelism (from WAVE_STARTED audit events) ----
        List<WorkflowEvent> waves = eventRepo.findByType("WAVE_STARTED");
        long parallelWaves = waves.stream().filter(e -> e.getPayloadJson() != null
                && e.getPayloadJson().contains("\"parallel\":true")).count();
        m.put("parallelism", Map.of(
                "totalWaves", waves.size(),
                "parallelWaves", parallelWaves,
                "note", "A parallel wave executed >1 independent task concurrently"));

        // ---- Approvals ----
        m.put("approvals", Map.of(
                "pending", approvalRepo.countByDecision(ApprovalDecision.PENDING),
                "approved", approvalRepo.countByDecision(ApprovalDecision.APPROVED),
                "rejected", approvalRepo.countByDecision(ApprovalDecision.REJECTED)));

        // ---- Replanning (selective + full replans recorded in the append-only audit log) ----
        m.put("replanning", Map.of(
                "totalReplans", eventRepo.findByType("WORKFLOW_REPLANNED").size(),
                "note", "Count of WORKFLOW_REPLANNED audit events (selective subgraph or full replans)"));

        // ---- Audit volume ----
        m.put("audit", Map.of("totalEvents", eventRepo.count()));

        return m;
    }

    private double ratio(long num, long denom) {
        return denom == 0 ? 0.0 : round((double) num / denom);
    }

    private double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
