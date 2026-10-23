package com.agentic.platform.orchestration.provider;

import com.agentic.platform.common.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Optional real LLM adapter (OpenAI-compatible chat completions). Activated only when
 * {@code app.llm.provider=openai} and credentials are supplied via environment. Model
 * output is treated strictly as untrusted data by callers.
 *
 * <p>Not exercised against a live endpoint in environments without credentials — see
 * assumptions-and-limitations.md.
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "openai")
public class OpenAiLlmProvider implements LlmProvider {

    private final AppProperties props;
    private final RestClient client;

    public OpenAiLlmProvider(AppProperties props) {
        this(props, RestClient.builder()
                .baseUrl(props.getLlm().getBaseUrl().isBlank()
                        ? "https://api.openai.com" : props.getLlm().getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getLlm().getApiKey())
                .build());
    }

    // Package-visible constructor for tests with a stubbed RestClient.
    OpenAiLlmProvider(AppProperties props, RestClient client) {
        this.props = props;
        this.client = client;
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public String model() {
        return props.getLlm().getModel();
    }

    @Override
    public boolean isMock() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public LlmResponse complete(LlmRequest request) {
        Map<String, Object> body = Map.of(
                "model", props.getLlm().getModel(),
                "max_tokens", request.maxTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", request.systemPrompt()),
                        Map.of("role", "user", "content", request.userPrompt())));

        Map<String, Object> resp = client.post()
                .uri("/v1/chat/completions")
                .body(body)
                .retrieve()
                .body(Map.class);

        String text = extractText(resp);
        return new LlmResponse(text, name(), model(), false);
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> resp) {
        if (resp == null) {
            return "";
        }
        Object choices = resp.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof Map<?, ?> first
                && first.get("message") instanceof Map<?, ?> message) {
            Object content = message.get("content");
            return content == null ? "" : content.toString();
        }
        return "";
    }
}
