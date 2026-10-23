package com.agentic.platform.orchestration.engine;

import com.agentic.platform.common.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(new AppProperties());

    @Test
    void onlyTransientFailuresAreRetryable() {
        assertThat(policy.isRetryable(new TransientAgentException("flaky"))).isTrue();
        assertThat(policy.isRetryable(new RuntimeException("validation"))).isFalse();
        assertThat(policy.isRetryable(new IllegalStateException("policy"))).isFalse();
    }

    @Test
    void canRetryRespectsBudgetAndClassification() {
        Throwable transient_ = new TransientAgentException("x");
        assertThat(policy.canRetry(1, 3, transient_)).isTrue();
        assertThat(policy.canRetry(2, 3, transient_)).isTrue();
        assertThat(policy.canRetry(3, 3, transient_)).isFalse(); // budget exhausted
        assertThat(policy.canRetry(1, 3, new RuntimeException())).isFalse(); // non-transient
    }

    @Test
    void backoffIsBoundedAndNonNegative() {
        long max = new AppProperties().getOrchestration().getBackoffMaxMillis();
        for (int attempt = 0; attempt < 10; attempt++) {
            long b = policy.backoffMillis(attempt);
            assertThat(b).isBetween(0L, max);
        }
    }
}
