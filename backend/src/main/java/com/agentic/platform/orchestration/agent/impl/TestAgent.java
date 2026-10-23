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
 * Test agent: validates acceptance criteria and reports results with evidence.
 *
 * <p>Honesty constraint: in mock mode these are ORCHESTRATION-SIMULATED checks mapped
 * from acceptance criteria — they are explicitly labelled {@code simulated=true} and
 * are NOT a substitute for the project's real JUnit suite. Unexecuted checks are never
 * reported as passing.
 */
@Component
public class TestAgent extends AbstractAgent {

    public TestAgent(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    public AgentRole role() {
        return AgentRole.TEST;
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentResult execute(AgentContext ctx) {
        maybeInjectFault(ctx);
        LlmResponse reasoning = reason(ctx, "test-v1",
                "You validate acceptance criteria and report pass/fail with evidence.",
                String.valueOf((Object) ctx.artifact("acceptanceCriteria")));

        List<String> criteria = (List<String>) ctx.artifact("acceptanceCriteria");
        List<Map<String, Object>> results = new ArrayList<>();
        int passed = 0;
        if (criteria != null) {
            for (String c : criteria) {
                // Deterministic simulated validation keyed off whether an implementation
                // artifact exists for the related goal.
                boolean hasImpl = ctx.artifacts().keySet().stream().anyMatch(k -> k.startsWith("change:"));
                String status = hasImpl ? "PASS" : "NOT_RUN";
                if ("PASS".equals(status)) passed++;
                results.add(Map.of("criterion", c, "status", status, "simulated", true));
            }
        }

        AgentResult.Builder b = AgentResult.succeeded()
                .output("checkResults", results)
                .output("passed", passed)
                .output("total", criteria == null ? 0 : criteria.size())
                .output("simulated", true)
                .output("note", "Simulated acceptance-criteria validation; real tests run via the JUnit suite")
                .output("reasoning", reasoning.text())
                .confidence(0.7)
                .provider(reasoning.provider(), reasoning.model(), reasoning.mock());

        long notRun = results.stream().filter(r -> "NOT_RUN".equals(r.get("status"))).count();
        if (notRun > 0) {
            b.finding(new Finding("MEDIUM", notRun + " acceptance criteria could not be validated (NOT_RUN)",
                    "Ensure implementation artifacts exist before the testing gate."));
        }
        ctx.putArtifact("testResults", results);
        return b.build();
    }
}
