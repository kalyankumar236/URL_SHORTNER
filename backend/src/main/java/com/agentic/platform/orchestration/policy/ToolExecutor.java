package com.agentic.platform.orchestration.policy;

import com.agentic.platform.orchestration.domain.AgentRole;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controlled execution surface for agent-proposed tool use. Every tool invocation is
 * mediated by the {@link PolicyEngine} (deny-by-default) and constrained to a declared
 * workspace boundary. Nothing here executes arbitrary shell/network calls — this
 * prototype's "tools" are explicit, enumerated, side-effect-light operations so the
 * safety model can be demonstrated without exposing the host.
 */
@Component
public class ToolExecutor {

    private final PolicyEngine policyEngine;

    /** Enumerated, allowlisted tool identifiers. Anything else is rejected. */
    private static final Map<String, String> TOOL_ACTION = Map.of(
            "write_artifact", "write_artifact",
            "propose_change", "propose_change",
            "run_simulated_tests", "run_simulated_tests",
            "scan", "scan");

    public ToolExecutor(PolicyEngine policyEngine) {
        this.policyEngine = policyEngine;
    }

    /**
     * Attempt a tool invocation on behalf of a role.
     *
     * @throws ToolDeniedException if policy denies the action, the tool is unknown, the
     *                             action requires approval, or the path escapes the workspace.
     */
    public ToolResult invoke(AgentRole role, String tool, String workspaceRoot, String targetPath) {
        String action = TOOL_ACTION.get(tool);
        if (action == null) {
            throw new ToolDeniedException("Unknown tool '" + tool + "' (not in allowlist)");
        }
        PolicyDecision decision = policyEngine.evaluate(role, action);
        if (!decision.allowed()) {
            throw new ToolDeniedException(decision.reason());
        }
        if (decision.requiresApproval()) {
            throw new ToolDeniedException("Tool '" + tool + "' requires human approval and cannot auto-execute");
        }
        if (targetPath != null && !withinWorkspace(workspaceRoot, targetPath)) {
            throw new ToolDeniedException("Path '" + targetPath + "' escapes workspace boundary '" + workspaceRoot + "'");
        }
        List<String> log = new ArrayList<>();
        log.add("policy-check: " + decision.reason());
        log.add("executed tool '" + tool + "' within '" + workspaceRoot + "'");
        return new ToolResult(true, tool, decision.risk().name(), log);
    }

    /** Reject path traversal / absolute escapes. */
    boolean withinWorkspace(String workspaceRoot, String targetPath) {
        if (workspaceRoot == null || targetPath == null) {
            return false;
        }
        String normalized = targetPath.replace('\\', '/');
        if (normalized.contains("..")) {
            return false;
        }
        // Relative paths (no leading slash, no drive letter) are confined to the workspace.
        boolean absolute = normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*");
        return !absolute;
    }

    public Map<String, String> allowedTools() {
        return TOOL_ACTION;
    }
}
