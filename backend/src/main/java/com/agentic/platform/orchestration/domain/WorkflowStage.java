package com.agentic.platform.orchestration.domain;

/** Lifecycle stages a workflow progresses through. */
public enum WorkflowStage {
    INTAKE,
    NORMALIZED,
    AMBIGUITY_ASSESSED,
    DESIGN,
    PLANNED,
    DAG_VALIDATED,
    EXECUTING,
    TESTING,
    SECURITY,
    DOCUMENTATION,
    RELEASE_REVIEW,
    COMPLETED
}
