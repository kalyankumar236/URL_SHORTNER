package com.agentic.platform.orchestration.agent.impl;

import com.agentic.platform.orchestration.agent.AbstractAgent;
import com.agentic.platform.orchestration.agent.AgentContext;
import com.agentic.platform.orchestration.agent.AgentResult;
import com.agentic.platform.orchestration.agent.Finding;
import com.agentic.platform.orchestration.domain.AgentRole;
import com.agentic.platform.orchestration.provider.LlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Release Readiness agent: consolidates evidence (tests, security, docs) into a
 * release-readiness report with an explicit per-check verdict. It recommends but does
 * not authorize release; the release gate + human approval own the final decision.
 */
@Component
public class ReleaseReadinessAgent extends AbstractAgent {

    public ReleaseReadinessAgent(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    public AgentRole role() {
        return AgentRole.RELEASE_READINESS;
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        maybeInjectFault(ctx);
        LlmResponse reasoning = reason(ctx, "release-v1",
                "You consolidate release evidence and recommend go/no-go with justification.",
                String.valueOf(ctx.artifacts().keySet()));

        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(check("acceptance-tests", ctx.artifact("testResults") != null));
        checks.add(check("security-clean", asInt(ctx.artifact("securityBlocking")) == 0));
        checks.add(check("documentation", ctx.artifact("documentation") != null));

        boolean recommendRelease = checks.stream().allMatch(c -> "PASS".equals(c.get("status")));

        AgentResult.Builder b = AgentResult.succeeded()
                .output("checks", checks)
                .output("recommendRelease", recommendRelease)
                .output("note", "Recommendation only; release requires the release gate and human approval")
                .output("reasoning", reasoning.text())
                .confidence(recommendRelease ? 0.85 : 0.5)
                .provider(reasoning.provider(), reasoning.model(), reasoning.mock());

        if (!recommendRelease) {
            b.finding(new Finding("HIGH", "Release readiness checks did not all pass",
                    "Resolve failing checks before requesting release approval."));
        }
        ctx.putArtifact("releaseReadiness", checks);
        return b.build();
    }

    private Map<String, Object> check(String name, boolean pass) {
        return Map.of("check", name, "status", pass ? "PASS" : "NOT_RUN");
    }

    private int asInt(Object o) {
        return (o instanceof Number n) ? n.intValue() : 0;
    }
}
