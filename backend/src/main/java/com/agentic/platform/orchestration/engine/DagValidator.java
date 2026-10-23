package com.agentic.platform.orchestration.engine;

import com.agentic.platform.orchestration.domain.Task;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates a task dependency graph: rejects references to missing tasks and detects
 * cycles via Kahn's algorithm. Pure, deterministic, and unit-tested.
 */
@Component
public class DagValidator {

    public record ValidationResult(boolean valid, String reason, List<String> topologicalOrder) {
        public static ValidationResult ok(List<String> order) {
            return new ValidationResult(true, "acyclic", order);
        }
        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason, List.of());
        }
    }

    public ValidationResult validate(List<Task> tasks) {
        Set<String> ids = new HashSet<>();
        for (Task t : tasks) {
            ids.add(t.getId());
        }

        // Reject missing dependencies.
        for (Task t : tasks) {
            for (String dep : t.getDependsOn()) {
                if (!ids.contains(dep)) {
                    return ValidationResult.invalid("Task '" + t.getName() + "' depends on missing task id: " + dep);
                }
                if (dep.equals(t.getId())) {
                    return ValidationResult.invalid("Task '" + t.getName() + "' depends on itself");
                }
            }
        }

        // Kahn's algorithm for topological sort / cycle detection.
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        for (Task t : tasks) {
            indegree.putIfAbsent(t.getId(), 0);
            adjacency.putIfAbsent(t.getId(), new ArrayList<>());
        }
        for (Task t : tasks) {
            for (String dep : t.getDependsOn()) {
                // Edge dep -> t (dep must finish before t).
                adjacency.get(dep).add(t.getId());
                indegree.merge(t.getId(), 1, Integer::sum);
            }
        }

        Deque<String> queue = new ArrayDeque<>();
        indegree.forEach((id, deg) -> {
            if (deg == 0) {
                queue.add(id);
            }
        });

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            order.add(id);
            for (String next : adjacency.get(id)) {
                if (indegree.merge(next, -1, Integer::sum) == 0) {
                    queue.add(next);
                }
            }
        }

        if (order.size() != tasks.size()) {
            return ValidationResult.invalid("Dependency graph contains a cycle");
        }
        return ValidationResult.ok(order);
    }
}
