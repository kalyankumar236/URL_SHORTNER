package com.agentic.platform.orchestration.provider;

/**
 * A model response. {@code mock} indicates deterministic offline output that is NOT
 * genuine model reasoning — callers and audit records must preserve this distinction.
 */
public record LlmResponse(
        String text,
        String provider,
        String model,
        boolean mock
) {
}
