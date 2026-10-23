package com.agentic.platform.orchestration.agent;

/** A structured finding produced by an agent (e.g., a security or quality issue). */
public record Finding(
        String severity,   // INFO, LOW, MEDIUM, HIGH, BLOCKER
        String message,
        String remediation
) {
    public boolean isBlocking() {
        return "HIGH".equalsIgnoreCase(severity) || "BLOCKER".equalsIgnoreCase(severity);
    }
}
