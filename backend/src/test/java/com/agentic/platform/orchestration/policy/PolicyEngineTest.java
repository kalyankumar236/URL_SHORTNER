package com.agentic.platform.orchestration.policy;

import com.agentic.platform.orchestration.domain.AgentRole;
import com.agentic.platform.orchestration.domain.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEngineTest {

    private final PolicyEngine engine = new PolicyEngine();

    @Test
    void permittedActionIsAllowedWithRisk() {
        PolicyDecision d = engine.evaluate(AgentRole.IMPLEMENTATION, "propose_change");
        assertThat(d.allowed()).isTrue();
        assertThat(d.requiresApproval()).isFalse();
        assertThat(d.risk()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void highImpactActionsRequireApproval() {
        PolicyDecision apply = engine.evaluate(AgentRole.IMPLEMENTATION, "apply_change");
        assertThat(apply.allowed()).isTrue();
        assertThat(apply.requiresApproval()).isTrue();
        assertThat(apply.risk()).isEqualTo(RiskLevel.HIGH);

        PolicyDecision release = engine.evaluate(AgentRole.RELEASE_READINESS, "release");
        assertThat(release.requiresApproval()).isTrue();
    }

    @Test
    void denyByDefaultForUnlistedAction() {
        PolicyDecision d = engine.evaluate(AgentRole.DOCUMENTATION, "propose_change");
        assertThat(d.allowed()).isFalse();
    }

    @Test
    void secretScanDetectsSecretsAndIgnoresCleanText() {
        assertThat(engine.scanForSecrets("api_key=ABCDEFGH12345XYZ")).isNotEmpty();
        assertThat(engine.scanForSecrets("password: hunter2longenough")).isNotEmpty();
        assertThat(engine.scanForSecrets("just some normal change text")).isEmpty();
        assertThat(engine.scanForSecrets(null)).isEmpty();
    }
}
