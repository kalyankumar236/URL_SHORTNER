package com.agentic.platform.orchestration.dto;

import jakarta.validation.constraints.Size;

/** A human approval decision. {@code approve=false} rejects and safely stops the workflow. */
public record ApprovalDecisionRequest(
        boolean approve,
        @Size(max = 512) String reason
) {
}
