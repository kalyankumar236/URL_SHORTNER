package com.agentic.platform.orchestration.policy;

/** Thrown when a tool invocation is rejected by policy or workspace boundaries. */
public class ToolDeniedException extends RuntimeException {
    public ToolDeniedException(String message) {
        super(message);
    }
}
