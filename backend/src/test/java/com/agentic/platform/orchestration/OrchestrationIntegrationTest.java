package com.agentic.platform.orchestration;

import com.agentic.platform.common.ApiException;
import com.agentic.platform.orchestration.domain.Approval;
import com.agentic.platform.orchestration.domain.ApprovalDecision;
import com.agentic.platform.orchestration.domain.GateOutcome;
import com.agentic.platform.orchestration.domain.GateResult;
import com.agentic.platform.orchestration.domain.Task;
import com.agentic.platform.orchestration.domain.TaskStatus;
import com.agentic.platform.orchestration.domain.Workflow;
import com.agentic.platform.orchestration.domain.WorkflowStatus;
import com.agentic.platform.orchestration.repo.GateResultRepository;
import com.agentic.platform.orchestration.repo.WorkflowEventRepository;
import com.agentic.platform.orchestration.service.ApprovalService;
import com.agentic.platform.orchestration.service.MetricsService;
import com.agentic.platform.orchestration.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end orchestration behaviour against the real engine (mock LLM provider).
 * Exercises the happy path (with human approvals), deterministic retry, retry-exhaustion
 * safe-stop, and separation-of-duties enforcement on approvals.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrchestrationIntegrationTest {

    @Autowired WorkflowService workflowService;
    @Autowired ApprovalService approvalService;
    @Autowired MetricsService metricsService;
    @Autowired WorkflowEventRepository eventRepo;
    @Autowired GateResultRepository gateRepo;

    private static final String REVIEWER = "qa-admin";
    private static final String REQUIREMENT =
            "Build a URL shortener with custom aliases, link expiration, and click analytics exposed via REST APIs, with tests and documentation";

    private int approvePending(String workflowId, boolean approve) {
        List<Approval> pending = approvalService.forWorkflow(workflowId).stream()
                .filter(a -> a.getDecision() == ApprovalDecision.PENDING)
                .toList();
        pending.forEach(a -> approvalService.decide(a.getId(), REVIEWER, approve, approve ? "ok" : "rejected"));
        return pending.size();
    }

    @Test
    void greenfieldHappyPathCompletesWithApprovals() {
        Workflow wf = workflowService.create(REQUIREMENT, "tester", null);

        // Pauses for the two high-risk implementation tasks.
        Workflow r1 = workflowService.run(wf.getId());
        assertThat(r1.getStatus()).isEqualTo(WorkflowStatus.WAITING_FOR_APPROVAL);
        assertThat(approvePending(wf.getId(), true)).isEqualTo(2);

        // Pauses again for the high-risk release-readiness task.
        Workflow r2 = workflowService.run(wf.getId());
        assertThat(r2.getStatus()).isEqualTo(WorkflowStatus.WAITING_FOR_APPROVAL);
        assertThat(approvePending(wf.getId(), true)).isEqualTo(1);

        Workflow r3 = workflowService.run(wf.getId());
        assertThat(r3.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        List<Task> tasks = workflowService.tasks(wf.getId());
        assertThat(tasks).hasSize(9);
        assertThat(tasks).allMatch(t -> t.getStatus() == TaskStatus.SUCCEEDED);

        // Every evaluated gate for a completed workflow should PASS.
        List<GateResult> gates = gateRepo.findByWorkflowIdOrderByCreatedAtAsc(wf.getId());
        assertThat(gates).isNotEmpty();
        assertThat(gates).allMatch(g -> g.getOutcome() == GateOutcome.PASS);

        // Append-only audit captured the lineage, including at least one parallel wave.
        assertThat(eventRepo.countByWorkflowId(wf.getId())).isGreaterThan(0);
        assertThat(eventRepo.findByWorkflowIdOrderBySeqAsc(wf.getId()))
                .anyMatch(e -> "WAVE_STARTED".equals(e.getType()) && e.getPayloadJson().contains("\"parallel\":true"));

        // Metrics are derived from persisted state and reflect this completion.
        Map<String, Object> metrics = metricsService.snapshot();
        assertThat(metrics).containsKeys("workflows", "tasks", "parallelism", "approvals", "latency");
    }

    @Test
    void transientFailuresRetryThenSucceed() {
        Map<String, String> inputs = Map.of(
                "impl-backend", "{\"faultInjection\":{\"transientFailures\":2}}");
        Workflow wf = workflowService.create(REQUIREMENT, "tester", inputs);

        workflowService.run(wf.getId());
        approvePending(wf.getId(), true); // approve the two implementation tasks
        workflowService.run(wf.getId());

        Task implBackend = workflowService.tasks(wf.getId()).stream()
                .filter(t -> t.getName().equals("impl-backend")).findFirst().orElseThrow();
        // transientFailures=2, maxAttempts=3 → fails twice, succeeds on the third attempt.
        assertThat(implBackend.getAttempts()).isEqualTo(3);
        assertThat(implBackend.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
    }

    @Test
    void retryExhaustionTriggersSafeStop() {
        Map<String, String> inputs = Map.of(
                "impl-backend", "{\"faultInjection\":{\"transientFailures\":5}}");
        Workflow wf = workflowService.create(REQUIREMENT, "tester", inputs);

        workflowService.run(wf.getId());
        approvePending(wf.getId(), true);
        Workflow after = workflowService.run(wf.getId());

        assertThat(after.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOP);
        Task implBackend = workflowService.tasks(wf.getId()).stream()
                .filter(t -> t.getName().equals("impl-backend")).findFirst().orElseThrow();
        assertThat(implBackend.getAttempts()).isEqualTo(3); // budget exhausted
        assertThat(implBackend.getStatus()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void approvalRejectionSafelyStops() {
        Workflow wf = workflowService.create(REQUIREMENT, "tester", null);
        workflowService.run(wf.getId());

        int rejected = approvePending(wf.getId(), false);
        assertThat(rejected).isEqualTo(2);

        Workflow after = workflowService.run(wf.getId());
        assertThat(after.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOP);
    }

    @Test
    void agentCannotApproveOwnActionAndHumanIsRequired() {
        Workflow wf = workflowService.create(REQUIREMENT, "tester", null);
        workflowService.run(wf.getId());
        Approval pending = approvalService.forWorkflow(wf.getId()).stream()
                .filter(a -> a.getDecision() == ApprovalDecision.PENDING)
                .findFirst().orElseThrow();

        // The proposer ("orchestrator") may not approve its own action.
        assertThatThrownBy(() -> approvalService.decide(pending.getId(), "orchestrator", true, null))
                .isInstanceOf(ApiException.class);
        // An agent role may never be a reviewer.
        assertThatThrownBy(() -> approvalService.decide(pending.getId(), "IMPLEMENTATION", true, null))
                .isInstanceOf(ApiException.class);
        // A human reviewer succeeds.
        Approval decided = approvalService.decide(pending.getId(), REVIEWER, true, "approved by human");
        assertThat(decided.getDecision()).isEqualTo(ApprovalDecision.APPROVED);
    }
}
