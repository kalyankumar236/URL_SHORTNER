package com.agentic.platform.orchestration.engine;

import com.agentic.platform.orchestration.domain.TaskStatus;
import com.agentic.platform.orchestration.domain.WorkflowStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic state machine defining the only legal transitions for workflows and
 * tasks. Illegal transitions throw {@link IllegalStateTransitionException}. This is
 * plain, unit-tested Java — an LLM can never cause an illegal transition.
 */
@Component
public class StateMachine {

    private final Map<WorkflowStatus, Set<WorkflowStatus>> workflowTransitions = new EnumMap<>(WorkflowStatus.class);
    private final Map<TaskStatus, Set<TaskStatus>> taskTransitions = new EnumMap<>(TaskStatus.class);

    public StateMachine() {
        // ---- Workflow transitions ----
        workflowTransitions.put(WorkflowStatus.PENDING,
                EnumSet.of(WorkflowStatus.RUNNING, WorkflowStatus.CANCELLED));
        workflowTransitions.put(WorkflowStatus.RUNNING,
                EnumSet.of(WorkflowStatus.WAITING_FOR_APPROVAL, WorkflowStatus.COMPLETED,
                        WorkflowStatus.FAILED, WorkflowStatus.CANCELLED, WorkflowStatus.SAFE_STOP,
                        WorkflowStatus.RUNNING));
        workflowTransitions.put(WorkflowStatus.WAITING_FOR_APPROVAL,
                EnumSet.of(WorkflowStatus.RUNNING, WorkflowStatus.COMPLETED,
                        WorkflowStatus.CANCELLED, WorkflowStatus.SAFE_STOP));
        // Terminal states — no outgoing transitions.
        workflowTransitions.put(WorkflowStatus.COMPLETED, EnumSet.noneOf(WorkflowStatus.class));
        workflowTransitions.put(WorkflowStatus.FAILED, EnumSet.noneOf(WorkflowStatus.class));
        workflowTransitions.put(WorkflowStatus.CANCELLED, EnumSet.noneOf(WorkflowStatus.class));
        workflowTransitions.put(WorkflowStatus.SAFE_STOP, EnumSet.noneOf(WorkflowStatus.class));

        // ---- Task transitions ----
        taskTransitions.put(TaskStatus.PENDING,
                EnumSet.of(TaskStatus.READY, TaskStatus.BLOCKED, TaskStatus.CANCELLED, TaskStatus.STALE));
        taskTransitions.put(TaskStatus.READY,
                EnumSet.of(TaskStatus.RUNNING, TaskStatus.WAITING_FOR_APPROVAL, TaskStatus.BLOCKED,
                        TaskStatus.CANCELLED, TaskStatus.STALE));
        taskTransitions.put(TaskStatus.RUNNING,
                EnumSet.of(TaskStatus.SUCCEEDED, TaskStatus.FAILED, TaskStatus.RETRYING,
                        TaskStatus.WAITING_FOR_APPROVAL, TaskStatus.CANCELLED));
        taskTransitions.put(TaskStatus.RETRYING,
                EnumSet.of(TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.CANCELLED));
        taskTransitions.put(TaskStatus.WAITING_FOR_APPROVAL,
                EnumSet.of(TaskStatus.RUNNING, TaskStatus.SUCCEEDED, TaskStatus.CANCELLED,
                        TaskStatus.FAILED, TaskStatus.STALE));
        taskTransitions.put(TaskStatus.BLOCKED,
                EnumSet.of(TaskStatus.READY, TaskStatus.CANCELLED, TaskStatus.STALE, TaskStatus.FAILED));
        taskTransitions.put(TaskStatus.STALE,
                EnumSet.of(TaskStatus.PENDING, TaskStatus.READY, TaskStatus.CANCELLED));
        // Terminal-ish states.
        taskTransitions.put(TaskStatus.SUCCEEDED, EnumSet.of(TaskStatus.STALE, TaskStatus.ROLLED_BACK));
        taskTransitions.put(TaskStatus.FAILED, EnumSet.of(TaskStatus.STALE, TaskStatus.PENDING));
        taskTransitions.put(TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class));
        taskTransitions.put(TaskStatus.ROLLED_BACK, EnumSet.of(TaskStatus.PENDING, TaskStatus.STALE));
    }

    public boolean canTransition(WorkflowStatus from, WorkflowStatus to) {
        return from == to || workflowTransitions.getOrDefault(from, Set.of()).contains(to);
    }

    public boolean canTransition(TaskStatus from, TaskStatus to) {
        return from == to || taskTransitions.getOrDefault(from, Set.of()).contains(to);
    }

    public void assertTransition(WorkflowStatus from, WorkflowStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateTransitionException("Illegal workflow transition: " + from + " -> " + to);
        }
    }

    public void assertTransition(TaskStatus from, TaskStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateTransitionException("Illegal task transition: " + from + " -> " + to);
        }
    }
}
