package com.agentic.platform.urlshortener.api;

import com.agentic.platform.common.AppProperties;
import com.agentic.platform.urlshortener.domain.Link;
import com.agentic.platform.urlshortener.service.AnalyticsService;
import com.agentic.platform.urlshortener.service.LinkService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Handles the actual short-link redirect at {@code GET /{code}}.
 *
 * <p>Fast path: resolve code, respond with the configured redirect status, and record
 * analytics asynchronously (never blocking the redirect). Only the stored destination
 * is used — a caller cannot supply an arbitrary redirect target.
 */
@RestController
public class RedirectController {

    private final LinkService linkService;
    private final AnalyticsService analyticsService;
    private final AppProperties props;

    public RedirectController(LinkService linkService, AnalyticsService analyticsService, AppProperties props) {
        this.linkService = linkService;
        this.analyticsService = analyticsService;
        this.props = props;
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        Link link = linkService.resolveForRedirect(code);
        int status = props.getShort().getDefaultRedirectStatus();

        // Record analytics off the hot path (privacy-conscious; best-effort).
        recordClick(link, status, request);

        return ResponseEntity.status(status)
                .location(URI.create(link.getDestinationUrl()))
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .build();
    }

    private void recordClick(Link link, int status, HttpServletRequest request) {
        String clientHash = analyticsService.hashClient(clientIp(request), request.getHeader("User-Agent"));
        String referrerHost = analyticsService.referrerHost(request.getHeader("Referer"));
        String uaClass = analyticsService.classifyUserAgent(request.getHeader("User-Agent"));
        analyticsService.recordAsync(link.getId(), status, clientHash, referrerHost, uaClass);
    }

    /** Best-effort client IP extraction; only used to derive a salted hash, never stored raw. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
