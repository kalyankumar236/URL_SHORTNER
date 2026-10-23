package com.agentic.platform.orchestration.repo;

import com.agentic.platform.orchestration.domain.WorkflowEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, String> {
    List<WorkflowEvent> findByWorkflowIdOrderBySeqAsc(String workflowId);
    long countByWorkflowId(String workflowId);
    List<WorkflowEvent> findByType(String type);
    long countByType(String type);
}
