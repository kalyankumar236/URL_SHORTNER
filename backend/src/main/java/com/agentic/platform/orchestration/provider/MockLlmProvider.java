package com.agentic.platform.orchestration.provider;

import com.agentic.platform.common.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic, offline LLM provider used by default and in tests. Produces stable,
 * reproducible output derived from a hash of the prompt. Its output is clearly marked
 * {@code mock=true} and must never be presented as genuine model reasoning.
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmProvider implements LlmProvider {

    private final String model;

    public MockLlmProvider(AppProperties props) {
        this.model = props.getLlm().getModel();
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public boolean isMock() {
        return true;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        // Deterministic: same prompt -> same short digest-based reasoning marker.
        String digest = shortHash(request.systemPrompt() + "\n" + request.userPrompt());
        String text = "[MOCK:" + request.promptTemplateVersion() + ":" + digest + "] "
                + "Deterministic offline reasoning for prompt template "
                + request.promptTemplateVersion() + ".";
        return new LlmResponse(text, name(), model, true);
    }

    private String shortHash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            return "00000000";
        }
    }
}
