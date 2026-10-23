package com.agentic.platform.orchestration.repo;

import com.agentic.platform.orchestration.domain.Workflow;
import com.agentic.platform.orchestration.domain.WorkflowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRepository extends JpaRepository<Workflow, String> {
    Page<Workflow> findAll(Pageable pageable);
    List<Workflow> findByStatus(WorkflowStatus status);
    long countByStatus(WorkflowStatus status);
}
