package com.agentic.platform.orchestration;

import com.agentic.platform.orchestration.domain.Approval;
import com.agentic.platform.orchestration.domain.ApprovalDecision;
import com.agentic.platform.orchestration.domain.GateOutcome;
import com.agentic.platform.orchestration.domain.Task;
import com.agentic.platform.orchestration.domain.TaskStatus;
import com.agentic.platform.orchestration.domain.Workflow;
import com.agentic.platform.orchestration.domain.WorkflowStatus;
import com.agentic.platform.orchestration.repo.GateResultRepository;
import com.agentic.platform.orchestration.repo.WorkflowEventRepository;
import com.agentic.platform.orchestration.service.ApprovalService;
import com.agentic.platform.orchestration.service.MetricsService;
import com.agentic.platform.orchestration.service.WorkflowService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproducible end-to-end execution of the three required scenarios (greenfield, brownfield,
 * ambiguous) driven from the on-disk fixtures under {@code /scenarios}. Everything runs against
 * the real engine in deterministic mock-provider mode — no paid LLM access required.
 *
 * <p>These are the runnable, asserted counterpart to the {@code docs} scenario walkthroughs:
 * each test loads the same {@code requirement.json} a human would read, executes it, and
 * verifies the claimed evidence (parallelism, bounded retries, ambiguity detection, and
 * <em>selective</em> replanning that preserves unaffected completed work).
 */
@SpringBootTest
@ActiveProfiles("test")
class ScenariosTest {

    @Autowired WorkflowService workflowService;
    @Autowired ApprovalService approvalService;
    @Autowired MetricsService metricsService;
    @Autowired WorkflowEventRepository eventRepo;
    @Autowired GateResultRepository gateRepo;
    @Autowired ObjectMapper mapper;

    private static final String REVIEWER = "qa-admin";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Fixture(String scenario, String requirement, Map<String, String> taskInputs,
                   String replanFromTask, String replanReason) {
    }

    private Fixture loadFixture(String name) throws IOException {
        // Surefire runs with the module dir as the working directory; scenarios live at repo root.
        Path p = Paths.get("..", "scenarios", name, "requirement.json");
        assertThat(Files.exists(p))
                .as("scenario fixture should exist at %s", p.toAbsolutePath())
                .isTrue();
        return mapper.readValue(Files.readString(p), Fixture.class);
    }

    private int approveAllPending(String workflowId) {
        List<Approval> pending = approvalService.forWorkflow(workflowId).stream()
                .filter(a -> a.getDecision() == ApprovalDecision.PENDING)
                .toList();
        pending.forEach(a -> approvalService.decide(a.getId(), REVIEWER, true, "approved"));
        return pending.size();
    }

    private Task task(String workflowId, String name) {
        return workflowService.tasks(workflowId).stream()
                .filter(t -> t.getName().equals(name))
                .findFirst().orElseThrow();
    }

    private long succeededCount(String workflowId, String taskId) {
        return eventRepo.findByWorkflowIdOrderBySeqAsc(workflowId).stream()
                .filter(e -> "TASK_SUCCEEDED".equals(e.getType()) && taskId.equals(e.getTaskId()))
                .count();
    }

    @Test
    void scenarioA_greenfield_completesWithApprovalsAndParallelism() throws IOException {
        Fixture f = loadFixture("greenfield");
        Workflow wf = workflowService.create(f.requirement(), "scenario-runner", f.taskInputs());

        // Pauses twice for human approval: the two high-risk implementation tasks, then release.
        Workflow r1 = workflowService.run(wf.getId());
        assertThat(r1.getStatus()).isEqualTo(WorkflowStatus.WAITING_FOR_APPROVAL);
        assertThat(approveAllPending(wf.getId())).isEqualTo(2);

        Workflow r2 = workflowService.run(wf.getId());
        assertThat(r2.getStatus()).isEqualTo(WorkflowStatus.WAITING_FOR_APPROVAL);
        assertThat(approveAllPending(wf.getId())).isEqualTo(1);

        Workflow r3 = workflowService.run(wf.getId());
        assertThat(r3.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        List<Task> tasks = workflowService.tasks(wf.getId());
        assertThat(tasks).hasSize(9).allMatch(t -> t.getStatus() == TaskStatus.SUCCEEDED);
        assertThat(gateRepo.findByWorkflowIdOrderByCreatedAtAsc(wf.getId()))
                .isNotEmpty().allMatch(g -> g.getOutcome() == GateOutcome.PASS);

        // Independent implementation tasks executed concurrently in one wave (sync barrier at testing).
        assertThat(eventRepo.findByWorkflowIdOrderBySeqAsc(wf.getId()))
                .anyMatch(e -> "WAVE_STARTED".equals(e.getType())
                        && e.getPayloadJson().contains("\"parallel\":true"));
    }

    @Test
    void scenarioB_brownfield_boundedRetriesRecoverTransientFailure() throws IOException {
        Fixture f = loadFixture("brownfield");
        Workflow wf = workflowService.create(f.requirement(), "scenario-runner", f.taskInputs());

        workflowService.run(wf.getId());
        approveAllPending(wf.getId());          // approve the two implementation tasks
        workflowService.run(wf.getId());

        Task implBackend = task(wf.getId(), "impl-backend");
        // transientFailures=2, maxAttempts=3 -> two bounded retries, then success (the very feature added).
        assertThat(implBackend.getAttempts()).isEqualTo(3);
        assertThat(implBackend.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);

        // The retry was actually executed and audited (simulated transient analytics-persistence failure).
        assertThat(eventRepo.findByWorkflowIdOrderBySeqAsc(wf.getId()))
                .anyMatch(e -> "TASK_RETRYING".equals(e.getType())
                        && implBackend.getId().equals(e.getTaskId()));

        // Unrelated independent work (frontend contract task) is preserved and still succeeds.
        assertThat(task(wf.getId(), "impl-frontend").getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
    }

    @Test
    void scenarioC_ambiguous_detectsAmbiguityThenSelectivelyReplans() throws IOException {
        Fixture f = loadFixture("ambiguous");
        Workflow wf = workflowService.create(f.requirement(), "scenario-runner", f.taskInputs());

        Workflow r1 = workflowService.run(wf.getId());
        assertThat(r1.getStatus()).isEqualTo(WorkflowStatus.WAITING_FOR_APPROVAL);

        // The requirement analyst detected the ambiguity ("faster", "more reliable") and recorded it.
        Task requirement = task(wf.getId(), "requirement-analysis");
        assertThat(requirement.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(requirement.getOutputRef()).contains("ambiguities").contains("Vague target");

        // A design decision changes -> selective replan from the design task.
        workflowService.replanFromTask(wf.getId(), f.replanFromTask(), f.replanReason(),
                REVIEWER, f.taskInputs());

        // Preserved: the normalized requirement stays valid. Invalidated: design + everything downstream.
        assertThat(task(wf.getId(), "requirement-analysis").getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(task(wf.getId(), "architecture-design").getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(task(wf.getId(), "release-readiness").getStatus()).isEqualTo(TaskStatus.PENDING);

        // The replan is audited with an explicit invalidated/preserved breakdown.
        assertThat(eventRepo.findByWorkflowIdOrderBySeqAsc(wf.getId()))
                .anyMatch(e -> "WORKFLOW_REPLANNED".equals(e.getType())
                        && e.getPayloadJson().contains("selective")
                        && e.getPayloadJson().contains("architecture-design")
                        && e.getPayloadJson().contains("requirement-analysis"));

        // Drive the revised plan to completion (impl + release approvals as before).
        workflowService.run(wf.getId());
        approveAllPending(wf.getId());
        Workflow r2 = workflowService.run(wf.getId());
        if (r2.getStatus() == WorkflowStatus.WAITING_FOR_APPROVAL) {
            approveAllPending(wf.getId());
            r2 = workflowService.run(wf.getId());
        }
        assertThat(r2.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        // Proof of SELECTIVE replanning: the preserved task ran exactly once; the invalidated
        // design task ran twice (original + re-run). The whole workflow was not blindly rerun.
        assertThat(succeededCount(wf.getId(), requirement.getId())).isEqualTo(1);
        assertThat(succeededCount(wf.getId(), task(wf.getId(), "architecture-design").getId())).isEqualTo(2);

        // Replanning is surfaced honestly in metrics.
        Map<String, Object> metrics = metricsService.snapshot();
        assertThat(metrics).containsKey("replanning");
    }
}
