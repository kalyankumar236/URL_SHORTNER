package com.agentic.platform.orchestration.engine;

import com.agentic.platform.orchestration.domain.TaskStatus;
import com.agentic.platform.orchestration.domain.WorkflowStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateMachineTest {

    private final StateMachine sm = new StateMachine();

    @Test
    void legalWorkflowTransitions() {
        assertThat(sm.canTransition(WorkflowStatus.PENDING, WorkflowStatus.RUNNING)).isTrue();
        assertThat(sm.canTransition(WorkflowStatus.RUNNING, WorkflowStatus.WAITING_FOR_APPROVAL)).isTrue();
        assertThat(sm.canTransition(WorkflowStatus.WAITING_FOR_APPROVAL, WorkflowStatus.RUNNING)).isTrue();
        assertThat(sm.canTransition(WorkflowStatus.RUNNING, WorkflowStatus.COMPLETED)).isTrue();
    }

    @Test
    void illegalWorkflowTransitionRejected() {
        assertThat(sm.canTransition(WorkflowStatus.PENDING, WorkflowStatus.COMPLETED)).isFalse();
        assertThat(sm.canTransition(WorkflowStatus.COMPLETED, WorkflowStatus.RUNNING)).isFalse();
        assertThatThrownBy(() -> sm.assertTransition(WorkflowStatus.COMPLETED, WorkflowStatus.RUNNING))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void legalTaskTransitions() {
        assertThat(sm.canTransition(TaskStatus.PENDING, TaskStatus.READY)).isTrue();
        assertThat(sm.canTransition(TaskStatus.READY, TaskStatus.RUNNING)).isTrue();
        assertThat(sm.canTransition(TaskStatus.RUNNING, TaskStatus.SUCCEEDED)).isTrue();
        assertThat(sm.canTransition(TaskStatus.RUNNING, TaskStatus.RETRYING)).isTrue();
        assertThat(sm.canTransition(TaskStatus.RETRYING, TaskStatus.RUNNING)).isTrue();
        assertThat(sm.canTransition(TaskStatus.WAITING_FOR_APPROVAL, TaskStatus.RUNNING)).isTrue();
    }

    @Test
    void illegalTaskTransitionRejected() {
        assertThat(sm.canTransition(TaskStatus.SUCCEEDED, TaskStatus.RUNNING)).isFalse();
        assertThat(sm.canTransition(TaskStatus.CANCELLED, TaskStatus.RUNNING)).isFalse();
        assertThatThrownBy(() -> sm.assertTransition(TaskStatus.CANCELLED, TaskStatus.RUNNING))
                .isInstanceOf(IllegalStateTransitionException.class);
    }
}
