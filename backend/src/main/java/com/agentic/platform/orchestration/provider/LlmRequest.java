package com.agentic.platform.orchestration.provider;

/**
 * A bounded request to a language model. Prompts are template-versioned and must not
 * contain secrets (the caller redacts). {@code maxTokens} bounds model cost/output.
 */
public record LlmRequest(
        String promptTemplateVersion,
        String systemPrompt,
        String userPrompt,
        int maxTokens
) {
    public static LlmRequest of(String templateVersion, String system, String user) {
        return new LlmRequest(templateVersion, system, user, 1024);
    }
}
