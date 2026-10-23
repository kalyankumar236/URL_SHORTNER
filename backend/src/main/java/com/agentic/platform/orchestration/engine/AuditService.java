package com.agentic.platform.orchestration.engine;

import com.agentic.platform.orchestration.domain.WorkflowEvent;
import com.agentic.platform.orchestration.repo.WorkflowEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Append-only audit log. Events are only ever inserted (never updated/deleted),
 * giving a tamper-evident decision lineage. A per-workflow lock guarantees a
 * gap-free monotonic {@code seq} even under parallel task execution.
 *
 * <p>Payloads are redacted for obvious secrets before persistence.
 */
@Service
public class AuditService {

    private static final Pattern SECRET_KEY = Pattern.compile(
            "(?i)(api[_-]?key|secret|password|token|authorization|bearer)");

    private final WorkflowEventRepository eventRepo;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public AuditService(WorkflowEventRepository eventRepo, ObjectMapper mapper) {
        this.eventRepo = eventRepo;
        this.mapper = mapper;
    }

    public WorkflowEvent append(String workflowId, String type, String taskId, Map<String, ?> payload, String actor) {
        ReentrantLock lock = locks.computeIfAbsent(workflowId, k -> new ReentrantLock());
        lock.lock();
        try {
            return doAppend(workflowId, type, taskId, redact(payload), actor);
        } finally {
            lock.unlock();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected WorkflowEvent doAppend(String workflowId, String type, String taskId, String payloadJson, String actor) {
        long seq = eventRepo.countByWorkflowId(workflowId) + 1;
        WorkflowEvent event = new WorkflowEvent(
                UUID.randomUUID().toString(), workflowId, seq, type, taskId, payloadJson, actor);
        return eventRepo.save(event);
    }

    private String redact(Map<String, ?> payload) {
        if (payload == null) {
            return null;
        }
        try {
            Map<String, Object> copy = new java.util.LinkedHashMap<>();
            payload.forEach((k, v) -> {
                if (k != null && SECRET_KEY.matcher(k).find()) {
                    copy.put(k, "***REDACTED***");
                } else {
                    copy.put(k, v);
                }
            });
            return mapper.writeValueAsString(copy);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"unserializable payload\"}";
        }
    }
}
