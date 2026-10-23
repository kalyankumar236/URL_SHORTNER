package com.agentic.platform.orchestration.repo;

import com.agentic.platform.orchestration.domain.GateResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GateResultRepository extends JpaRepository<GateResult, String> {
    List<GateResult> findByWorkflowIdOrderByCreatedAtAsc(String workflowId);
}
