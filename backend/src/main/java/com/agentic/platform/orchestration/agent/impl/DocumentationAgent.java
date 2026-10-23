package com.agentic.platform.orchestration.agent.impl;

import com.agentic.platform.orchestration.agent.AbstractAgent;
import com.agentic.platform.orchestration.agent.AgentContext;
import com.agentic.platform.orchestration.agent.AgentResult;
import com.agentic.platform.orchestration.domain.AgentRole;
import com.agentic.platform.orchestration.provider.LlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Documentation agent: produces a documentation summary (README deltas, API notes,
 * runbook pointers) consumed by the release-readiness evaluation.
 */
@Component
public class DocumentationAgent extends AbstractAgent {

    public DocumentationAgent(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    public AgentRole role() {
        return AgentRole.DOCUMENTATION;
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        maybeInjectFault(ctx);
        LlmResponse reasoning = reason(ctx, "docs-v1",
                "You produce concise, accurate setup and API documentation.",
                String.valueOf((Object) ctx.artifact("apiContract")));

        List<String> docs = List.of(
                "README: run commands and profiles",
                "API: endpoints, request/response, error codes",
                "Runbook: safe-stop, approvals, replanning");

        ctx.putArtifact("documentation", docs);

        return AgentResult.succeeded()
                .output("documents", docs)
                .output("reasoning", reasoning.text())
                .confidence(0.85)
                .provider(reasoning.provider(), reasoning.model(), reasoning.mock())
                .build();
    }
}
