package com.agentic.platform.orchestration.repo;

import com.agentic.platform.orchestration.domain.Approval;
import com.agentic.platform.orchestration.domain.ApprovalDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRepository extends JpaRepository<Approval, String> {
    List<Approval> findByWorkflowId(String workflowId);
    List<Approval> findByWorkflowIdAndDecision(String workflowId, ApprovalDecision decision);
    List<Approval> findByDecision(ApprovalDecision decision);
    long countByDecision(ApprovalDecision decision);
}
