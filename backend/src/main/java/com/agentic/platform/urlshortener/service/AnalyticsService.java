package com.agentic.platform.urlshortener.service;

import com.agentic.platform.common.AppProperties;
import com.agentic.platform.urlshortener.domain.ClickEvent;
import com.agentic.platform.urlshortener.dto.AnalyticsSummaryResponse;
import com.agentic.platform.urlshortener.dto.LinkAnalyticsResponse;
import com.agentic.platform.urlshortener.repo.ClickEventRepository;
import com.agentic.platform.urlshortener.repo.LinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Captures redirect events off the hot path and answers analytics queries.
 *
 * <p>Recording is asynchronous and best-effort: it never fails a redirect. Transient
 * persistence failures are retried a bounded number of times with no retry on
 * permanent failures (see {@link #isTransient}). Raw IPs are never stored.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final ClickEventRepository clickRepo;
    private final LinkRepository linkRepo;
    private final AppProperties props;

    public AnalyticsService(ClickEventRepository clickRepo, LinkRepository linkRepo, AppProperties props) {
        this.clickRepo = clickRepo;
        this.linkRepo = linkRepo;
        this.props = props;
    }

    /**
     * Record a redirect event asynchronously with bounded retries. Executed on the
     * bounded {@code analyticsExecutor}. Any failure is swallowed (logged) so it can
     * never affect redirect availability.
     */
    @Async("analyticsExecutor")
    public void recordAsync(Long linkId, int responseStatus, String clientHash, String referrerHost, String uaClass) {
        if (!props.getAnalytics().isEnabled()) {
            return;
        }
        int maxRetries = Math.max(0, props.getAnalytics().getMaxRetries());
        int attempt = 0;
        while (true) {
            try {
                persist(linkId, responseStatus, clientHash, referrerHost, uaClass);
                return;
            } catch (DataAccessException ex) {
                attempt++;
                if (!isTransient(ex) || attempt > maxRetries) {
                    log.warn("Dropping analytics event for link {} after {} attempt(s): {}",
                            linkId, attempt, ex.getClass().getSimpleName());
                    return;
                }
                sleepBackoff(attempt);
            } catch (RuntimeException ex) {
                log.warn("Unexpected analytics failure for link {}: {}", linkId, ex.getClass().getSimpleName());
                return;
            }
        }
    }

    @Transactional
    protected void persist(Long linkId, int responseStatus, String clientHash, String referrerHost, String uaClass) {
        clickRepo.save(new ClickEvent(linkId, Instant.now(), responseStatus, clientHash, referrerHost, uaClass));
    }

    /** Only transient data-access failures are retryable; validation/constraint errors are not. */
    boolean isTransient(DataAccessException ex) {
        return ex instanceof TransientDataAccessException
                || ex instanceof org.springframework.dao.QueryTimeoutException
                || ex instanceof org.springframework.dao.CannotAcquireLockException;
    }

    private void sleepBackoff(int attempt) {
        try {
            long base = 20L * attempt;
            Thread.sleep(Math.min(200, base));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- Queries --------------------------------------------------------------

    @Transactional(readOnly = true)
    public LinkAnalyticsResponse linkAnalytics(String shortCode, Long linkId, Instant from, Instant to) {
        long total = clickRepo.countByLinkId(linkId);
        long inRange = clickRepo.countByLinkIdAndOccurredAtBetween(linkId, from, to);
        List<ClickEvent> events = clickRepo.findByLinkIdAndOccurredAtBetweenOrderByOccurredAtAsc(linkId, from, to);
        Map<String, Long> daily = new TreeMap<>();
        for (ClickEvent e : events) {
            daily.merge(DAY.format(e.getOccurredAt()), 1L, Long::sum);
        }
        return new LinkAnalyticsResponse(shortCode, total, inRange, daily);
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(Instant from, Instant to, int topN) {
        long inRange = clickRepo.countByOccurredAtBetween(from, to);
        long total = clickRepo.count();
        List<Object[]> rows = clickRepo.topLinks(PageRequest.of(0, Math.max(1, topN)));
        List<AnalyticsSummaryResponse.TopLinkEntry> top = new java.util.ArrayList<>();
        Map<Long, String> codeCache = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long linkId = ((Number) row[0]).longValue();
            long clicks = ((Number) row[1]).longValue();
            String code = codeCache.computeIfAbsent(linkId,
                    id -> linkRepo.findById(id).map(l -> l.getShortCode()).orElse("(deleted)"));
            top.add(new AnalyticsSummaryResponse.TopLinkEntry(code, clicks));
        }
        return new AnalyticsSummaryResponse(total, inRange, top);
    }

    // ---- Privacy-conscious helpers -------------------------------------------

    /** Salted, truncated hash of a client identifier. Raw IPs are never stored. */
    public String hashClient(String clientIp, String userAgent) {
        if (clientIp == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(props.getAnalytics().getHashSalt().getBytes(StandardCharsets.UTF_8));
            md.update(clientIp.getBytes(StandardCharsets.UTF_8));
            if (userAgent != null) {
                md.update(userAgent.getBytes(StandardCharsets.UTF_8));
            }
            byte[] digest = md.digest();
            // Truncate to 16 bytes (32 hex chars) — enough to distinguish, not to reverse.
            byte[] truncated = new byte[16];
            System.arraycopy(digest, 0, truncated, 0, 16);
            return HexFormat.of().formatHex(truncated);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /** Extract only the referrer host (never the full referrer URL). */
    public String referrerHost(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            String host = new URI(referer).getHost();
            return host == null ? null : (host.length() > 255 ? host.substring(0, 255) : host);
        } catch (Exception e) {
            return null;
        }
    }

    /** Coarse user-agent classification — deliberately low-resolution for privacy. */
    public String classifyUserAgent(String ua) {
        if (ua == null || ua.isBlank()) {
            return "unknown";
        }
        String l = ua.toLowerCase();
        if (l.contains("bot") || l.contains("crawler") || l.contains("spider")) return "bot";
        if (l.contains("mobile") || l.contains("android") || l.contains("iphone")) return "mobile";
        if (l.contains("mozilla") || l.contains("chrome") || l.contains("safari") || l.contains("firefox")) return "desktop";
        return "other";
    }
}
