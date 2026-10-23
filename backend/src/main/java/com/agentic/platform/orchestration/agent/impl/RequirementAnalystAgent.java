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
import java.util.Locale;

/**
 * Requirement Analyst: normalizes the request, extracts goals and acceptance criteria,
 * flags ambiguity, and assigns a confidence score. Low confidence surfaces as a
 * finding that downstream gates/approvals can react to.
 */
@Component
public class RequirementAnalystAgent extends AbstractAgent {

    public RequirementAnalystAgent(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    public AgentRole role() {
        return AgentRole.REQUIREMENT_ANALYST;
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        maybeInjectFault(ctx);
        String requirement = ctx.workflow().getOriginalRequirement();
        LlmResponse reasoning = reason(ctx, "req-analyst-v1",
                "You analyze software requirements into goals, acceptance criteria, and ambiguities.",
                requirement);

        String normalized = requirement.trim().replaceAll("\\s+", " ");

        List<String> goals = extractGoals(normalized);
        List<String> acceptance = deriveAcceptanceCriteria(goals);
        List<String> ambiguities = detectAmbiguities(normalized);
        List<String> assumptions = defaultAssumptions(ambiguities);

        // Confidence drops with ambiguity density.
        double confidence = Math.max(0.3, 1.0 - 0.15 * ambiguities.size());

        ctx.putArtifact("normalizedRequirement", normalized);
        ctx.putArtifact("goals", goals);
        ctx.putArtifact("acceptanceCriteria", acceptance);
        ctx.putArtifact("ambiguities", ambiguities);
        ctx.putArtifact("assumptions", assumptions);

        AgentResult.Builder b = AgentResult.succeeded()
                .output("normalizedRequirement", normalized)
                .output("goals", goals)
                .output("acceptanceCriteria", acceptance)
                .output("ambiguities", ambiguities)
                .output("assumptions", assumptions)
                .output("reasoning", reasoning.text())
                .confidence(confidence)
                .provider(reasoning.provider(), reasoning.model(), reasoning.mock());

        if (!ambiguities.isEmpty()) {
            b.finding(new Finding("MEDIUM",
                    "Requirement contains ambiguity: " + String.join("; ", ambiguities),
                    "Capture explicit assumptions or request human clarification before high-risk work."));
        }
        if (acceptance.isEmpty()) {
            b.finding(new Finding("HIGH", "No acceptance criteria could be derived",
                    "Clarify the requirement; the requirement gate requires acceptance criteria."));
        }
        return b.build();
    }

    private List<String> extractGoals(String text) {
        List<String> goals = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("url shortener") || lower.contains("short link")) {
            goals.add("Provide URL shortening with redirect resolution");
        }
        if (lower.contains("alias")) goals.add("Support custom aliases");
        if (lower.contains("expir")) goals.add("Support link expiration");
        if (lower.contains("analytic") || lower.contains("click")) goals.add("Capture and report click analytics");
        if (lower.contains("rest") || lower.contains("api")) goals.add("Expose REST APIs");
        if (lower.contains("test")) goals.add("Provide automated tests");
        if (lower.contains("doc")) goals.add("Provide setup documentation");
        if (lower.contains("reliab") || lower.contains("retry")) goals.add("Improve reliability");
        if (lower.contains("fast") || lower.contains("performance") || lower.contains("faster"))
            goals.add("Improve performance");
        if (goals.isEmpty()) goals.add("Deliver the described capability");
        return goals;
    }

    private List<String> deriveAcceptanceCriteria(List<String> goals) {
        List<String> criteria = new ArrayList<>();
        for (String g : goals) {
            criteria.add("Given the feature, when exercised, then '" + g + "' is demonstrably satisfied");
        }
        return criteria;
    }

    private List<String> detectAmbiguities(String text) {
        List<String> ambiguities = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        // Vague adjectives without measurable targets.
        for (String vague : List.of("faster", "fast", "more reliable", "better", "scalable", "secure")) {
            if (lower.contains(vague) && !lower.matches(".*\\d.*")) {
                ambiguities.add("Vague target '" + vague + "' lacks a measurable objective");
            }
        }
        return ambiguities;
    }

    private List<String> defaultAssumptions(List<String> ambiguities) {
        List<String> assumptions = new ArrayList<>();
        for (String a : ambiguities) {
            if (a.contains("faster") || a.contains("fast")) {
                assumptions.add("Proposed target: p95 redirect latency < 50ms at prototype scale (proposed, not measured)");
            }
            if (a.contains("reliable")) {
                assumptions.add("Proposed target: redirect availability >= 99.9% with bounded analytics retries (proposed)");
            }
        }
        return assumptions;
    }
}
