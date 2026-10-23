package com.agentic.platform.orchestration.domain;

/** Overall workflow status. See StateMachine for legal transitions. */
public enum WorkflowStatus {
    PENDING,
    RUNNING,
    WAITING_FOR_APPROVAL,
    COMPLETED,
    FAILED,
    CANCELLED,
    SAFE_STOP
}
