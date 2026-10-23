package com.agentic.platform.urlshortener.service;

import com.agentic.platform.common.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token-bucket rate limiter (default backend). Correct for a single node;
 * the {@code docker}/{@code prod} profiles would swap in a Redis-backed limiter for
 * horizontal correctness (see ADR-007).
 *
 * <p>Fallback policy: if {@code app.rate-limit.fail-open} is true, limiter errors do
 * not block traffic (a warning is logged). This avoids turning a limiter outage into
 * a full outage.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final AppProperties props;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(AppProperties props) {
        this.props = props;
    }

    /** @return true if the request may proceed, false if it must be rejected (429). */
    public boolean tryAcquire(String key) {
        if (!props.getRateLimit().isEnabled()) {
            return true;
        }
        try {
            Bucket bucket = buckets.computeIfAbsent(key,
                    k -> new Bucket(props.getRateLimit().getCapacity(), props.getRateLimit().getRefillPerMinute()));
            return bucket.tryConsume();
        } catch (RuntimeException e) {
            // Fallback policy.
            if (props.getRateLimit().isFailOpen()) {
                log.warn("Rate limiter error; failing open for key hash", e);
                return true;
            }
            return false;
        }
    }

    /** Simple time-based token bucket. */
    private static final class Bucket {
        private final int capacity;
        private final double refillPerMillis;
        private double tokens;
        private long lastRefill;

        Bucket(int capacity, int refillPerMinute) {
            this.capacity = capacity;
            this.refillPerMillis = refillPerMinute / 60000.0;
            this.tokens = capacity;
            this.lastRefill = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            double refill = (now - lastRefill) * refillPerMillis;
            if (refill > 0) {
                tokens = Math.min(capacity, tokens + refill);
                lastRefill = now;
            }
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
