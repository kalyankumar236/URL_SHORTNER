package com.agentic.platform.orchestration.engine;

import com.agentic.platform.common.AppProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Deterministic retry classification and backoff computation.
 *
 * <p>Only transient failures are retryable; validation/policy failures are terminal.
 * Backoff is exponential with full jitter, capped at a configured maximum, to avoid
 * retry storms and thundering herds.
 */
@Component
public class RetryPolicy {

    private final AppProperties props;

    public RetryPolicy(AppProperties props) {
        this.props = props;
    }

    /** A failure is retryable only if explicitly marked transient. */
    public boolean isRetryable(Throwable error) {
        return error instanceof TransientAgentException;
    }

    public boolean canRetry(int attempts, int maxAttempts, Throwable error) {
        return isRetryable(error) && attempts < maxAttempts;
    }

    /** Exponential backoff with full jitter: random in [0, min(max, base * 2^attempt)]. */
    public long backoffMillis(int attempt) {
        long base = props.getOrchestration().getBackoffBaseMillis();
        long max = props.getOrchestration().getBackoffMaxMillis();
        long exp = (long) (base * Math.pow(2, Math.max(0, attempt)));
        long cap = Math.min(max, Math.max(base, exp));
        return ThreadLocalRandom.current().nextLong(0, cap + 1);
    }
}
