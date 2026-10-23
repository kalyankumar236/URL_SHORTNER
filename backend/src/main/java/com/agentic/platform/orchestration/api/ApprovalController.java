package com.agentic.platform.orchestration.api;

import com.agentic.platform.orchestration.domain.Approval;
import com.agentic.platform.orchestration.dto.ApprovalDecisionRequest;
import com.agentic.platform.orchestration.dto.ApprovalResponse;
import com.agentic.platform.orchestration.service.ApprovalService;
import com.agentic.platform.orchestration.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Human approval API. Listing and deciding require ROLE_ADMIN. Separation of duties is
 * enforced in {@link ApprovalService}: an agent can never be the reviewer, and the
 * proposer can never approve its own action. On a decision the workflow is resumed.
 */
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final WorkflowService workflowService;

    public ApprovalController(ApprovalService approvalService, WorkflowService workflowService) {
        this.approvalService = approvalService;
        this.workflowService = workflowService;
    }

    @GetMapping
    public List<ApprovalResponse> pending() {
        return approvalService.pending().stream().map(ApprovalResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ApprovalResponse get(@PathVariable String id) {
        return ApprovalResponse.from(approvalService.get(id));
    }

    @PostMapping("/{id}/decision")
    public ApprovalResponse decide(@PathVariable String id,
                                   @Valid @RequestBody ApprovalDecisionRequest req,
                                   Authentication auth) {
        String reviewer = auth != null ? auth.getName() : "admin";
        Approval decided = approvalService.decide(id, reviewer, req.approve(), req.reason());
        // Resume the workflow: approved tasks proceed; a rejection drives a safe stop.
        workflowService.run(decided.getWorkflowId());
        return ApprovalResponse.from(approvalService.get(id));
    }
}
