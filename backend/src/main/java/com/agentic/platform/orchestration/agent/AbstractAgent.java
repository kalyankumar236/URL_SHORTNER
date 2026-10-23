package com.agentic.platform.orchestration.agent;

import com.agentic.platform.orchestration.engine.TransientAgentException;
import com.agentic.platform.orchestration.provider.LlmRequest;
import com.agentic.platform.orchestration.provider.LlmResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared agent behaviour: deterministic LLM reasoning capture, input parsing, and a
 * fault-injection hook used by scenarios/tests to exercise retries and safe-stop.
 */
public abstract class AbstractAgent implements Agent {

    protected final ObjectMapper mapper;

    protected AbstractAgent(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Capture model reasoning (mock by default) and tag the result with provider info. */
    protected LlmResponse reason(AgentContext ctx, String templateVersion, String system, String user) {
        return ctx.llm().complete(LlmRequest.of(templateVersion, system, user));
    }

    protected JsonNode parseInput(AgentContext ctx) {
        try {
            String ref = ctx.task().getInputRef();
            if (ref == null || ref.isBlank()) {
                return mapper.createObjectNode();
            }
            return mapper.readTree(ref);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    /**
     * Fault-injection hook. If the task input declares
     * {@code {"faultInjection":{"transientFailures":N}}}, this throws a transient
     * failure until the task's attempt count exceeds N. Enables deterministic retry
     * and safe-stop demonstrations without real flaky dependencies.
     */
    protected void maybeInjectFault(AgentContext ctx) {
        JsonNode input = parseInput(ctx);
        JsonNode fi = input.path("faultInjection");
        if (fi.isMissingNode()) {
            return;
        }
        int transientFailures = fi.path("transientFailures").asInt(0);
        if (ctx.task().getAttempts() <= transientFailures) {
            throw new TransientAgentException("Injected transient failure (attempt "
                    + ctx.task().getAttempts() + " of configured " + transientFailures + ")");
        }
    }
}
