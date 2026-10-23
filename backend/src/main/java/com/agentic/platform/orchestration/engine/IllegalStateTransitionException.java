package com.agentic.platform.orchestration.engine;

/** Thrown when an illegal workflow or task state transition is attempted. */
public class IllegalStateTransitionException extends RuntimeException {
    public IllegalStateTransitionException(String message) {
        super(message);
    }
}
