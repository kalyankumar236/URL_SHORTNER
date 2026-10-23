package com.agentic.platform.orchestration.engine;

import com.agentic.platform.orchestration.domain.AgentRole;
import com.agentic.platform.orchestration.domain.RiskLevel;
import com.agentic.platform.orchestration.domain.Task;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DagValidatorTest {

    private final DagValidator validator = new DagValidator();

    private Task task(String id, String name, String... deps) {
        Task t = new Task(id, "wf", name, AgentRole.IMPLEMENTATION, RiskLevel.LOW, 3, 1);
        t.setDependsOn(Set.of(deps));
        return t;
    }

    @Test
    void validLinearChainIsAcyclic() {
        Task a = task("a", "a");
        Task b = task("b", "b", "a");
        Task c = task("c", "c", "b");
        DagValidator.ValidationResult r = validator.validate(List.of(a, b, c));
        assertThat(r.valid()).isTrue();
        assertThat(r.topologicalOrder()).containsExactly("a", "b", "c");
    }

    @Test
    void missingDependencyIsInvalid() {
        Task a = task("a", "a", "ghost");
        DagValidator.ValidationResult r = validator.validate(List.of(a));
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).contains("missing task id");
    }

    @Test
    void selfDependencyIsInvalid() {
        Task a = task("a", "a", "a");
        DagValidator.ValidationResult r = validator.validate(List.of(a));
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).contains("itself");
    }

    @Test
    void cycleIsDetected() {
        Task a = task("a", "a", "c");
        Task b = task("b", "b", "a");
        Task c = task("c", "c", "b");
        DagValidator.ValidationResult r = validator.validate(List.of(a, b, c));
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).contains("cycle");
    }
}
