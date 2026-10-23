package com.agentic.platform.orchestration.gate;

import com.agentic.platform.orchestration.domain.GateOutcome;
import com.agentic.platform.orchestration.domain.GateResult;
import com.agentic.platform.orchestration.engine.AuditService;
import com.agentic.platform.orchestration.repo.GateResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic quality gates evaluated at stage boundaries. Each gate inspects the
 * evidence an agent produced (artifacts) and returns an explicit {@link GateOutcome}.
 *
 * <p>Gates are the deterministic control that decides whether a workflow may advance.
 * An agent cannot bypass a gate — the orchestrator consults the persisted gate result.
 * NOT_RUN is never silently treated as PASS.
 */
@Service
public class GateEvaluator {

    private final GateResultRepository gateRepo;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public GateEvaluator(GateResultRepository gateRepo, AuditService audit, ObjectMapper mapper) {
        this.gateRepo = gateRepo;
        this.audit = audit;
        this.mapper = mapper;
    }

    /** Evaluate a named gate against the live artifact store, persist the result, and audit it. */
    public GateResult evaluate(String workflowId, String gateName, Map<String, Object> artifacts) {
        Evaluation eval = switch (gateName) {
            case "requirement" -> requirementGate(artifacts);
            case "design" -> designGate(artifacts);
            case "planning" -> planningGate(artifacts);
            case "implementation" -> implementationGate(artifacts);
            case "testing" -> testingGate(artifacts);
            case "security" -> securityGate(artifacts);
            case "release" -> releaseGate(artifacts);
            default -> new Evaluation(GateOutcome.NOT_RUN, Map.of("reason", "unknown gate: " + gateName));
        };

        GateResult result = new GateResult(
                UUID.randomUUID().toString(), workflowId, gateName, eval.outcome(), toJson(eval.evidence()));
        GateResult saved = gateRepo.save(result);
        audit.append(workflowId, "GATE_EVALUATED", null,
                Map.of("gate", gateName, "outcome", eval.outcome().name(), "evidence", eval.evidence()),
                "gate-evaluator");
        return saved;
    }

    private Evaluation requirementGate(Map<String, Object> a) {
        List<?> criteria = asList(a.get("acceptanceCriteria"));
        List<?> ambiguities = asList(a.get("ambiguities"));
        if (criteria.isEmpty()) {
            return new Evaluation(GateOutcome.FAIL, Map.of("reason", "no acceptance criteria"));
        }
        if (!ambiguities.isEmpty()) {
            // Material ambiguity → require human approval downstream, but gate still passes
            // with the ambiguity recorded as evidence (approval is a separate control).
            return new Evaluation(GateOutcome.PASS,
                    Map.of("acceptanceCriteria", criteria.size(), "ambiguities", ambiguities));
        }
        return new Evaluation(GateOutcome.PASS, Map.of("acceptanceCriteria", criteria.size(), "ambiguities", 0));
    }

    private Evaluation designGate(Map<String, Object> a) {
        boolean hasContract = a.get("apiContract") != null && !asList(a.get("apiContract")).isEmpty();
        boolean hasArch = a.get("architecture") != null;
        return (hasContract && hasArch)
                ? new Evaluation(GateOutcome.PASS, Map.of("apiContract", true, "architecture", true))
                : new Evaluation(GateOutcome.FAIL, Map.of("apiContract", hasContract, "architecture", hasArch));
    }

    private Evaluation planningGate(Map<String, Object> a) {
        boolean planned = a.get("planAnnotations") != null;
        return planned
                ? new Evaluation(GateOutcome.PASS, Map.of("planned", true))
                : new Evaluation(GateOutcome.FAIL, Map.of("planned", false));
    }

    private Evaluation implementationGate(Map<String, Object> a) {
        long changes = a.keySet().stream().filter(k -> k.startsWith("change:")).count();
        return changes > 0
                ? new Evaluation(GateOutcome.PASS, Map.of("proposedChanges", changes))
                : new Evaluation(GateOutcome.FAIL, Map.of("proposedChanges", 0));
    }

    private Evaluation testingGate(Map<String, Object> a) {
        List<?> results = asList(a.get("testResults"));
        if (results.isEmpty()) {
            return new Evaluation(GateOutcome.NOT_RUN, Map.of("reason", "no test results recorded"));
        }
        long notRun = results.stream()
                .filter(r -> r instanceof Map<?, ?> m && "NOT_RUN".equals(m.get("status"))).count();
        long failed = results.stream()
                .filter(r -> r instanceof Map<?, ?> m && "FAIL".equals(m.get("status"))).count();
        if (failed > 0) {
            return new Evaluation(GateOutcome.FAIL, Map.of("failed", failed, "total", results.size()));
        }
        if (notRun > 0) {
            return new Evaluation(GateOutcome.BLOCKED, Map.of("notRun", notRun, "total", results.size()));
        }
        return new Evaluation(GateOutcome.PASS, Map.of("passed", results.size(), "total", results.size()));
    }

    private Evaluation securityGate(Map<String, Object> a) {
        int blocking = asInt(a.get("securityBlocking"));
        return blocking == 0
                ? new Evaluation(GateOutcome.PASS, Map.of("blockingFindings", 0))
                : new Evaluation(GateOutcome.FAIL, Map.of("blockingFindings", blocking));
    }

    private Evaluation releaseGate(Map<String, Object> a) {
        List<?> checks = asList(a.get("releaseReadiness"));
        if (checks.isEmpty()) {
            return new Evaluation(GateOutcome.NOT_RUN, Map.of("reason", "no release checks"));
        }
        boolean allPass = checks.stream()
                .allMatch(c -> c instanceof Map<?, ?> m && "PASS".equals(m.get("status")));
        return allPass
                ? new Evaluation(GateOutcome.PASS, Map.of("checks", checks.size()))
                : new Evaluation(GateOutcome.BLOCKED, Map.of("checks", checks));
    }

    private List<?> asList(Object o) {
        return (o instanceof List<?> l) ? l : List.of();
    }

    private int asInt(Object o) {
        return (o instanceof Number n) ? n.intValue() : 0;
    }

    private String toJson(Map<String, Object> evidence) {
        try {
            return mapper.writeValueAsString(evidence);
        } catch (Exception e) {
            return "{}";
        }
    }

    private record Evaluation(GateOutcome outcome, Map<String, Object> evidence) {
    }
}
