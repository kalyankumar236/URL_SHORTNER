package com.agentic.platform.orchestration.dto;

import com.agentic.platform.orchestration.domain.GateResult;

import java.time.Instant;

/** Public view of a deterministic gate evaluation result. */
public record GateResultResponse(
        String gateName,
        String outcome,
        String evidenceJson,
        Instant createdAt
) {
    public static GateResultResponse from(GateResult g) {
        return new GateResultResponse(g.getGateName(), g.getOutcome().name(), g.getEvidenceJson(), g.getCreatedAt());
    }
}
