package com.agentic.platform.orchestration.agent.impl;

import com.agentic.platform.orchestration.agent.AbstractAgent;
import com.agentic.platform.orchestration.agent.AgentContext;
import com.agentic.platform.orchestration.agent.AgentResult;
import com.agentic.platform.orchestration.domain.AgentRole;
import com.agentic.platform.orchestration.provider.LlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Implementation: proposes code changes within an approved workspace and records a
 * change summary plus associated tests. It CANNOT approve its own high-risk changes —
 * approval is enforced by the engine, not here. Supports fault injection for retry demos.
 */
@Component
public class ImplementationAgent extends AbstractAgent {

    public ImplementationAgent(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    public AgentRole role() {
        return AgentRole.IMPLEMENTATION;
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        // Fault injection happens first so retries/exhaustion can be demonstrated.
        maybeInjectFault(ctx);

        String taskName = ctx.task().getName();
        LlmResponse reasoning = reason(ctx, "impl-v1",
                "You propose minimal, convention-following code changes with tests.",
                taskName + " :: " + ctx.artifact("apiContract"));

        List<String> changedArtifacts = List.of(
                "proposed/" + slug(taskName) + "/change-summary.md",
                "proposed/" + slug(taskName) + "/tests.md");

        Map<String, Object> changeSummary = Map.of(
                "task", taskName,
                "summary", "Proposed changes for '" + taskName + "' following existing conventions",
                "changedArtifacts", changedArtifacts,
                "isolated", true,
                "diffRetained", true);

        ctx.putArtifact("change:" + taskName, changeSummary);

        return AgentResult.succeeded()
                .output("changeSummary", changeSummary)
                .output("associatedTests", List.of(taskName + " unit test", taskName + " integration test"))
                .output("reasoning", reasoning.text())
                .confidence(0.75)
                .provider(reasoning.provider(), reasoning.model(), reasoning.mock())
                .build();
    }

    private String slug(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
