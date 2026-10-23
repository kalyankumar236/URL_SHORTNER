package com.agentic.platform.urlshortener.api;

import com.agentic.platform.urlshortener.domain.Link;
import com.agentic.platform.urlshortener.dto.AnalyticsSummaryResponse;
import com.agentic.platform.urlshortener.dto.LinkAnalyticsResponse;
import com.agentic.platform.urlshortener.service.AnalyticsService;
import com.agentic.platform.urlshortener.service.LinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Analytics endpoints (admin-protected). Per-link analytics and a cross-link summary.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Analytics", description = "Click analytics (admin only)")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final LinkService linkService;

    public AnalyticsController(AnalyticsService analyticsService, LinkService linkService) {
        this.analyticsService = analyticsService;
        this.linkService = linkService;
    }

    @GetMapping("/links/{code}/analytics")
    @Operation(summary = "Analytics for a single link")
    public LinkAnalyticsResponse linkAnalytics(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Link link = linkService.getByCode(code);
        Instant[] window = window(from, to);
        return analyticsService.linkAnalytics(code, link.getId(), window[0], window[1]);
    }

    @GetMapping("/analytics/summary")
    @Operation(summary = "Aggregate analytics across all links")
    public AnalyticsSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "10") int topN) {
        Instant[] window = window(from, to);
        return analyticsService.summary(window[0], window[1], Math.min(Math.max(topN, 1), 50));
    }

    /** Default window is the last 30 days when not provided. */
    private Instant[] window(Instant from, Instant to) {
        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(30, ChronoUnit.DAYS);
        return new Instant[]{start, end};
    }
}
