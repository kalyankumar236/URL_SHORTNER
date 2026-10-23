package com.agentic.platform.urlshortener.dto;

import java.util.List;

/** Aggregate analytics across all links. */
public record AnalyticsSummaryResponse(
        long totalClicks,
        long clicksInRange,
        List<TopLinkEntry> topLinks
) {
    public record TopLinkEntry(String shortCode, long clicks) {
    }
}
