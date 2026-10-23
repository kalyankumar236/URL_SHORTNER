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
 * Planning: confirms the task decomposition and identifies which tasks are independent
 * (parallelizable) versus dependent. The concrete DAG is created at workflow build
 * time; this agent validates and annotates it.
 */
@Component
public class PlanningAgent extends AbstractAgent {

    public PlanningAgent(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    public AgentRole role() {
        return AgentRole.PLANNING;
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        maybeInjectFault(ctx);
        LlmResponse reasoning = reason(ctx, "planning-v1",
                "You decompose work into a dependency-aware task graph with parallelizable tasks.",
                String.valueOf((Object) ctx.artifact("goals")));

        List<String> parallelizable = List.of(
                "Frontend contract task and Backend schema/API task run in parallel after the planning gate");

        ctx.putArtifact("planAnnotations", parallelizable);

        return AgentResult.succeeded()
                .output("parallelizable", parallelizable)
                .output("validationStrategy", "Each task declares validation criteria; integration is a sync barrier")
                .output("reasoning", reasoning.text())
                .confidence(0.85)
                .provider(reasoning.provider(), reasoning.model(), reasoning.mock())
                .build();
    }
}
