package com.agentic.platform.orchestration.provider;

/**
 * Pluggable LLM provider adapter. Implementations must treat model output as
 * untrusted data, never as executable instructions.
 */
public interface LlmProvider {

    /** Provider name, e.g. {@code mock}, {@code openai}. */
    String name();

    /** Model identifier when available. */
    String model();

    /** True if this provider returns deterministic, non-model (mock) output. */
    boolean isMock();

    /** Produce a bounded completion for the given request. */
    LlmResponse complete(LlmRequest request);
}
