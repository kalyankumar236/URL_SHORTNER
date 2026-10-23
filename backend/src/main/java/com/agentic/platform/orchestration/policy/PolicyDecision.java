package com.agentic.platform.orchestration.policy;

import com.agentic.platform.orchestration.domain.RiskLevel;

/**
 * Immutable result of a deterministic policy evaluation.
 *
 * @param allowed          whether the action is permitted at all
 * @param requiresApproval whether a human approval is required before execution
 * @param risk             the risk classification driving approval
 * @param reason           human-readable justification (for audit)
 */
public record PolicyDecision(boolean allowed, boolean requiresApproval, RiskLevel risk, String reason) {

    public static PolicyDecision deny(String reason) {
        return new PolicyDecision(false, false, RiskLevel.HIGH, reason);
    }

    public static PolicyDecision allow(RiskLevel risk, boolean requiresApproval, String reason) {
        return new PolicyDecision(true, requiresApproval, risk, reason);
    }
}
