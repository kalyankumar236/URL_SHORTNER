package com.agentic.platform.orchestration.agent;

import com.agentic.platform.orchestration.domain.AgentRole;

/**
 * An agent performs one role in the SDLC workflow. Agents PROPOSE and PRODUCE
 * structured artifacts; they never enforce gates, approvals, or policy — that is the
 * deterministic engine's job. Agent output is treated as untrusted until validated.
 */
public interface Agent {

    /** The role this agent fulfils. */
    AgentRole role();

    /** Execute the agent's work for the given task, returning a structured result. */
    AgentResult execute(AgentContext context);
}
