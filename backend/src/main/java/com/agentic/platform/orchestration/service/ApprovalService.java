package com.agentic.platform.orchestration.service;

import com.agentic.platform.common.ApiException;
import com.agentic.platform.orchestration.domain.AgentRole;
import com.agentic.platform.orchestration.domain.Approval;
import com.agentic.platform.orchestration.domain.ApprovalDecision;
import com.agentic.platform.orchestration.domain.RiskLevel;
import com.agentic.platform.orchestration.engine.AuditService;
import com.agentic.platform.orchestration.repo.ApprovalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backend-enforced human approval. The engine — not any agent — decides when an
 * approval is required and whether it has been granted. Separation of duties is
 * enforced here: the reviewer must be a human and must not be the proposing agent.
 */
@Service
public class ApprovalService {

    private final ApprovalRepository approvalRepo;
    private final AuditService audit;

    private static final List<String> AGENT_ROLE_NAMES =
            Arrays.stream(AgentRole.values()).map(Enum::name).toList();

    public ApprovalService(ApprovalRepository approvalRepo, AuditService audit) {
        this.approvalRepo = approvalRepo;
        this.audit = audit;
    }

    @Transactional
    public Approval request(String workflowId, String taskId, String actionSummary,
                            RiskLevel risk, String affectedJson, String requestedBy) {
        Approval approval = new Approval(UUID.randomUUID().toString(), workflowId, taskId,
                actionSummary, risk, affectedJson, requestedBy);
        Approval saved = approvalRepo.save(approval);
        audit.append(workflowId, "APPROVAL_REQUESTED", taskId,
                Map.of("approvalId", saved.getId(), "risk", risk.name(), "action", actionSummary,
                        "requestedBy", requestedBy == null ? "system" : requestedBy),
                "orchestrator");
        return saved;
    }

    @Transactional
    public Approval decide(String approvalId, String reviewer, boolean approve, String reason) {
        Approval approval = approvalRepo.findById(approvalId)
                .orElseThrow(() -> ApiException.notFound("Approval not found: " + approvalId));

        if (approval.getDecision() != ApprovalDecision.PENDING) {
            throw ApiException.conflict("Approval already decided: " + approval.getDecision());
        }
        if (reviewer == null || reviewer.isBlank()) {
            throw ApiException.forbidden("A human reviewer identity is required");
        }
        // Separation of duties: an agent can never be the reviewer, and the proposer
        // can never approve its own action.
        if (AGENT_ROLE_NAMES.contains(reviewer.toUpperCase())) {
            throw ApiException.forbidden("Agents may not approve actions; a human reviewer is required");
        }
        if (reviewer.equalsIgnoreCase(approval.getRequestedBy())) {
            throw ApiException.forbidden("The proposer may not approve its own action");
        }

        approval.setDecision(approve ? ApprovalDecision.APPROVED : ApprovalDecision.REJECTED);
        approval.setReviewer(reviewer);
        approval.setReason(reason);
        approval.setDecidedAt(Instant.now());
        Approval saved = approvalRepo.save(approval);

        audit.append(approval.getWorkflowId(), "APPROVAL_DECIDED", approval.getTaskId(),
                Map.of("approvalId", approvalId, "decision", saved.getDecision().name(),
                        "reviewer", reviewer, "reason", reason == null ? "" : reason),
                reviewer);
        return saved;
    }

    public List<Approval> pending() {
        return approvalRepo.findByDecision(ApprovalDecision.PENDING);
    }

    public List<Approval> forWorkflow(String workflowId) {
        return approvalRepo.findByWorkflowId(workflowId);
    }

    public Approval get(String approvalId) {
        return approvalRepo.findById(approvalId)
                .orElseThrow(() -> ApiException.notFound("Approval not found: " + approvalId));
    }
}
