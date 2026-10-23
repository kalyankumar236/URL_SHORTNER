package com.agentic.platform.orchestration.domain;

/**
 * Deterministic gate outcome. NOT_RUN is distinct from FAIL so release readiness
 * can honestly report checks that never executed.
 */
public enum GateOutcome {
    PASS,
    FAIL,
    BLOCKED,
    NOT_RUN
}
