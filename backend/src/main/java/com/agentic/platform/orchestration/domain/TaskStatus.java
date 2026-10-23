package com.agentic.platform.orchestration.domain;

/** Task execution status. See StateMachine for legal transitions. */
public enum TaskStatus {
    PENDING,
    READY,
    RUNNING,
    BLOCKED,
    WAITING_FOR_APPROVAL,
    RETRYING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    ROLLED_BACK,
    STALE
}
