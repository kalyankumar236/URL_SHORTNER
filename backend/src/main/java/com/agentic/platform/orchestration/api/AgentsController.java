package com.agentic.platform.orchestration.api;

import com.agentic.platform.orchestration.domain.AgentRole;
import com.agentic.platform.orchestration.policy.PolicyEngine;
import com.agentic.platform.orchestration.provider.LlmProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Exposes the agent roster and each role's permitted actions, plus the active LLM
 * provider and whether it is a deterministic mock. Public read — this is transparency
 * about what agents are allowed to do.
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentsController {

    private final PolicyEngine policyEngine;
    private final LlmProvider llmProvider;

    public AgentsController(PolicyEngine policyEngine, LlmProvider llmProvider) {
        this.policyEngine = policyEngine;
        this.llmProvider = llmProvider;
    }

    @GetMapping
    public Map<String, Object> agents() {
        List<Map<String, Object>> roster = java.util.Arrays.stream(AgentRole.values())
                .map(role -> Map.<String, Object>of(
                        "role", role.name(),
                        "permittedActions", policyEngine.allowedActions().getOrDefault(role, java.util.Set.of())))
                .toList();
        return Map.of(
                "provider", Map.of(
                        "name", llmProvider.name(),
                        "model", llmProvider.model(),
                        "mock", llmProvider.isMock()),
                "agents", roster);
    }
}
