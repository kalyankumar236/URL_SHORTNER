package com.agentic.platform.orchestration.policy;

import com.agentic.platform.orchestration.domain.AgentRole;
import com.agentic.platform.orchestration.domain.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic policy authority. This is the control plane that constrains what agents
 * (untrusted proposers) may do. It is DENY-BY-DEFAULT: an action is permitted only if it
 * appears in the role's explicit allowlist. High-impact actions are additionally marked
 * as requiring human approval, which the engine enforces independently of any agent.
 *
 * <p>Agents never evaluate their own permissions; they call into this engine and the
 * orchestrator enforces the decision.
 */
@Component
public class PolicyEngine {

    /** Per-role allowlist of action identifiers. Anything not listed is denied. */
    private static final Map<AgentRole, Set<String>> ALLOWED_ACTIONS = Map.of(
            AgentRole.REQUIREMENT_ANALYST, Set.of("reason", "write_artifact"),
            AgentRole.ARCHITECT, Set.of("reason", "write_artifact"),
            AgentRole.PLANNING, Set.of("reason", "write_artifact"),
            AgentRole.IMPLEMENTATION, Set.of("reason", "write_artifact", "propose_change"),
            AgentRole.TEST, Set.of("reason", "write_artifact", "run_simulated_tests"),
            AgentRole.SECURITY, Set.of("reason", "write_artifact", "scan"),
            AgentRole.DOCUMENTATION, Set.of("reason", "write_artifact"),
            AgentRole.RELEASE_READINESS, Set.of("reason", "write_artifact", "recommend_release"));

    /** Actions that always require a human approval regardless of role. */
    private static final Set<String> APPROVAL_REQUIRED = Set.of("apply_change", "release", "delete_data");

    /** High-risk action identifiers (drive risk classification and approvals). */
    private static final Set<String> HIGH_RISK = Set.of("apply_change", "release", "delete_data");
    private static final Set<String> MEDIUM_RISK = Set.of("propose_change", "scan");

    // Secret-detection patterns used by advisory scanning.
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("(?i)(api[_-]?key|secret|password|passwd|token)\\s*[=:]\\s*['\"]?[A-Za-z0-9/+_.-]{8,}"),
            Pattern.compile("(?i)aws_secret_access_key\\s*[=:]\\s*['\"]?[A-Za-z0-9/+]{20,}"),
            Pattern.compile("-----BEGIN (RSA |EC )?PRIVATE KEY-----"),
            Pattern.compile("(?i)Authorization:\\s*Bearer\\s+[A-Za-z0-9._-]{10,}"));

    /**
     * Evaluate whether {@code role} may perform {@code action}. Deny-by-default.
     */
    public PolicyDecision evaluate(AgentRole role, String action) {
        Set<String> allowed = ALLOWED_ACTIONS.getOrDefault(role, Set.of());
        boolean permitted = allowed.contains(action);
        if (!permitted && !APPROVAL_REQUIRED.contains(action)) {
            return PolicyDecision.deny("Action '" + action + "' not in allowlist for role " + role);
        }
        RiskLevel risk = classifyRisk(action);
        boolean needsApproval = APPROVAL_REQUIRED.contains(action);
        if (needsApproval && !permitted) {
            // Approval-gated actions are allowed to be *proposed*, but require approval to execute.
            return PolicyDecision.allow(risk, true,
                    "Action '" + action + "' requires human approval before execution");
        }
        return PolicyDecision.allow(risk, needsApproval,
                "Action '" + action + "' permitted for role " + role
                        + (needsApproval ? " (approval required)" : ""));
    }

    public RiskLevel classifyRisk(String action) {
        if (HIGH_RISK.contains(action)) return RiskLevel.HIGH;
        if (MEDIUM_RISK.contains(action)) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    /** Advisory secret scan. Returns a list of matched pattern descriptions (never the secret). */
    public List<String> scanForSecrets(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return SECRET_PATTERNS.stream()
                .filter(p -> p.matcher(text).find())
                .map(p -> "matched pattern /" + p.pattern().substring(0, Math.min(24, p.pattern().length())) + ".../")
                .toList();
    }

    public Map<AgentRole, Set<String>> allowedActions() {
        return ALLOWED_ACTIONS;
    }

    public Set<String> approvalRequiredActions() {
        return APPROVAL_REQUIRED;
    }
}
