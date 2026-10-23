package com.agentic.platform.orchestration.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured result returned by every agent. Captures status, produced artifacts,
 * findings, a confidence score where meaningful, and error details on failure.
 */
public class AgentResult {

    public enum Status { SUCCEEDED, FAILED }

    private final Status status;
    private final Map<String, Object> outputs;
    private final List<Finding> findings;
    private final Double confidence;
    private final String errorDetail;
    private final String providerName;
    private final String modelId;
    private final boolean mockProvider;

    private AgentResult(Status status, Map<String, Object> outputs, List<Finding> findings,
                        Double confidence, String errorDetail, String providerName,
                        String modelId, boolean mockProvider) {
        this.status = status;
        this.outputs = outputs;
        this.findings = findings;
        this.confidence = confidence;
        this.errorDetail = errorDetail;
        this.providerName = providerName;
        this.modelId = modelId;
        this.mockProvider = mockProvider;
    }

    public static Builder succeeded() {
        return new Builder(Status.SUCCEEDED);
    }

    public static AgentResult failed(String errorDetail) {
        return new Builder(Status.FAILED).error(errorDetail).build();
    }

    public Status getStatus() { return status; }
    public Map<String, Object> getOutputs() { return outputs; }
    public List<Finding> getFindings() { return findings; }
    public Double getConfidence() { return confidence; }
    public String getErrorDetail() { return errorDetail; }
    public String getProviderName() { return providerName; }
    public String getModelId() { return modelId; }
    public boolean isMockProvider() { return mockProvider; }

    public boolean hasBlockingFindings() {
        return findings.stream().anyMatch(Finding::isBlocking);
    }

    public static final class Builder {
        private final Status status;
        private final Map<String, Object> outputs = new LinkedHashMap<>();
        private final List<Finding> findings = new ArrayList<>();
        private Double confidence;
        private String errorDetail;
        private String providerName;
        private String modelId;
        private boolean mockProvider;

        private Builder(Status status) {
            this.status = status;
        }

        public Builder output(String key, Object value) {
            this.outputs.put(key, value);
            return this;
        }

        public Builder finding(Finding f) {
            this.findings.add(f);
            return this;
        }

        public Builder confidence(Double c) {
            this.confidence = c;
            return this;
        }

        public Builder error(String e) {
            this.errorDetail = e;
            return this;
        }

        public Builder provider(String name, String model, boolean mock) {
            this.providerName = name;
            this.modelId = model;
            this.mockProvider = mock;
            return this;
        }

        public AgentResult build() {
            return new AgentResult(status, outputs, findings, confidence, errorDetail,
                    providerName, modelId, mockProvider);
        }
    }
}
