package com.agentic.platform.orchestration.repo;

import com.agentic.platform.orchestration.domain.Task;
import com.agentic.platform.orchestration.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, String> {
    List<Task> findByWorkflowId(String workflowId);
    List<Task> findByWorkflowIdAndStatus(String workflowId, TaskStatus status);
    long countByWorkflowIdAndStatus(String workflowId, TaskStatus status);
    long countByStatus(TaskStatus status);
}
