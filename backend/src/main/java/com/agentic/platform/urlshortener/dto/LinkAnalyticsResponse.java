package com.agentic.platform.urlshortener.dto;

import java.util.Map;

/** Analytics for a single link. */
public record LinkAnalyticsResponse(
        String shortCode,
        long totalClicks,
        long clicksInRange,
        Map<String, Long> dailyClicks
) {
}
