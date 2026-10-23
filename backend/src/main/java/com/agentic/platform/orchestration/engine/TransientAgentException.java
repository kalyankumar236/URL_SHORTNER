package com.agentic.platform.orchestration.engine;

/** Marks a transient, retryable agent/task failure (e.g., simulated flaky dependency). */
public class TransientAgentException extends RuntimeException {
    public TransientAgentException(String message) {
        super(message);
    }
}
