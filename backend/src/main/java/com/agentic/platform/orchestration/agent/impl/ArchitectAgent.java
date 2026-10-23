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
 * Architect: proposes components, API contracts, a data model, and records design
 * decisions and dependencies/risks consumed by the design gate.
 */
@Component
public class ArchitectAgent extends AbstractAgent {

    public ArchitectAgent(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    public AgentRole role() {
        return AgentRole.ARCHITECT;
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        maybeInjectFault(ctx);
        LlmResponse reasoning = reason(ctx, "architect-v1",
                "You propose a minimal, maintainable architecture with explicit API contracts.",
                String.valueOf((Object) ctx.artifact("normalizedRequirement")));

        List<Map<String, Object>> apiContract = List.of(
                Map.of("method", "POST", "path", "/api/v1/links", "purpose", "create short link"),
                Map.of("method", "GET", "path", "/{code}", "purpose", "redirect"),
                Map.of("method", "GET", "path", "/api/v1/links/{code}/analytics", "purpose", "per-link analytics"));

        List<String> components = List.of("LinkController", "RedirectController", "LinkService",
                "AnalyticsService", "ShortCodeGenerator", "UrlValidator");

        Map<String, Object> dataModel = Map.of(
                "link", List.of("id", "short_code(unique)", "destination_url", "status", "expires_at"),
                "click_event", List.of("id", "link_id(fk)", "occurred_at", "response_status"));

        List<Map<String, String>> decisions = List.of(
                Map.of("decision", "Async best-effort analytics", "rationale", "keep redirect hot path fast"),
                Map.of("decision", "DB unique constraint for codes", "rationale", "collision-safe under concurrency"));

        ctx.putArtifact("apiContract", apiContract);
        ctx.putArtifact("architecture", Map.of("components", components, "dataModel", dataModel, "decisions", decisions));

        return AgentResult.succeeded()
                .output("components", components)
                .output("apiContract", apiContract)
                .output("dataModel", dataModel)
                .output("decisions", decisions)
                .output("risks", List.of("Alias collisions under concurrency", "Analytics backpressure"))
                .output("reasoning", reasoning.text())
                .confidence(0.8)
                .provider(reasoning.provider(), reasoning.model(), reasoning.mock())
                .build();
    }
}
