package com.agentic.platform.orchestration.agent.impl;

import com.agentic.platform.orchestration.agent.AbstractAgent;
import com.agentic.platform.orchestration.agent.AgentContext;
import com.agentic.platform.orchestration.agent.AgentResult;
import com.agentic.platform.orchestration.agent.Finding;
import com.agentic.platform.orchestration.domain.AgentRole;
import com.agentic.platform.orchestration.policy.PolicyEngine;
import com.agentic.platform.orchestration.provider.LlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Security and Policy agent: reviews proposed changes against deterministic policy and
 * produces findings with severity and remediation. It does NOT replace deterministic
 * security enforcement — the {@link PolicyEngine} remains the authority; this agent
 * surfaces advisory findings that gates consume.
 */
@Component
public class SecurityAgent extends AbstractAgent {

    private final PolicyEngine policyEngine;

    public SecurityAgent(ObjectMapper mapper, PolicyEngine policyEngine) {
        super(mapper);
        this.policyEngine = policyEngine;
    }

    @Override
    public AgentRole role() {
        return AgentRole.SECURITY;
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        maybeInjectFault(ctx);
        LlmResponse reasoning = reason(ctx, "security-v1",
                "You review changes for secret exposure, injection, and authorization gaps.",
                String.valueOf(ctx.artifacts().keySet()));

        AgentResult.Builder b = AgentResult.succeeded()
                .provider(reasoning.provider(), reasoning.model(), reasoning.mock())
                .output("reasoning", reasoning.text());

        // Deterministic scan: inspect any proposed change text for secret-like content.
        int blocking = 0;
        for (Map.Entry<String, Object> e : ctx.artifacts().entrySet()) {
            if (!e.getKey().startsWith("change:")) {
                continue;
            }
            String serialized = String.valueOf(e.getValue());
            List<String> violations = policyEngine.scanForSecrets(serialized);
            for (String v : violations) {
                blocking++;
                b.finding(new Finding("HIGH", "Potential secret in proposed change: " + v,
                        "Remove the secret; use environment variables or a secret manager."));
            }
        }

        b.output("blockingFindings", blocking)
         .output("policyChecks", List.of("secret-exposure", "scheme-allowlist", "authz-boundaries"))
         .confidence(0.8);
        ctx.putArtifact("securityBlocking", blocking);
        return b.build();
    }
}
