package com.agentic.platform.orchestration.api;

import com.agentic.platform.orchestration.policy.PolicyEngine;
import com.agentic.platform.orchestration.policy.ToolExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes the deterministic policy model: per-role allowed actions (deny-by-default),
 * actions that always require human approval, and the enumerated tool allowlist. Public
 * read — the safety model is transparent and auditable.
 */
@RestController
@RequestMapping("/api/v1/policies")
public class PoliciesController {

    private final PolicyEngine policyEngine;
    private final ToolExecutor toolExecutor;

    public PoliciesController(PolicyEngine policyEngine, ToolExecutor toolExecutor) {
        this.policyEngine = policyEngine;
        this.toolExecutor = toolExecutor;
    }

    @GetMapping
    public Map<String, Object> policies() {
        return Map.of(
                "model", "deny-by-default",
                "allowedActionsByRole", policyEngine.allowedActions().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue)),
                "approvalRequiredActions", policyEngine.approvalRequiredActions(),
                "allowedTools", toolExecutor.allowedTools().keySet());
    }
}
