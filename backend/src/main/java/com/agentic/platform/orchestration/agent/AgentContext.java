package com.agentic.platform.orchestration.agent;

import com.agentic.platform.orchestration.domain.Task;
import com.agentic.platform.orchestration.domain.Workflow;
import com.agentic.platform.orchestration.provider.LlmProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Execution context passed to an agent. Provides the workflow, the specific task, the
 * LLM provider, and a thread-safe shared artifact store so later agents can consume
 * earlier agents' outputs (decision lineage within a run).
 */
public class AgentContext {

    private final Workflow workflow;
    private final Task task;
    private final LlmProvider llmProvider;
    private final Map<String, Object> sharedArtifacts;

    public AgentContext(Workflow workflow, Task task, LlmProvider llmProvider,
                        Map<String, Object> sharedArtifacts) {
        this.workflow = workflow;
        this.task = task;
        this.llmProvider = llmProvider;
        this.sharedArtifacts = sharedArtifacts != null ? sharedArtifacts : new ConcurrentHashMap<>();
    }

    public Workflow workflow() { return workflow; }
    public Task task() { return task; }
    public LlmProvider llm() { return llmProvider; }
    public Map<String, Object> artifacts() { return sharedArtifacts; }

    @SuppressWarnings("unchecked")
    public <T> T artifact(String key) {
        return (T) sharedArtifacts.get(key);
    }

    public void putArtifact(String key, Object value) {
        sharedArtifacts.put(key, value);
    }
}
