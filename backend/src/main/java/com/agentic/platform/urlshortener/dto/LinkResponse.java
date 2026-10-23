package com.agentic.platform.urlshortener.dto;

import com.agentic.platform.urlshortener.domain.Link;
import com.agentic.platform.urlshortener.domain.LinkStatus;

import java.time.Instant;

/**
 * Public representation of a link. Deliberately omits internal numeric id and
 * creator identity to avoid exposing sensitive internals.
 */
public record LinkResponse(
        String shortCode,
        String shortUrl,
        String destinationUrl,
        LinkStatus status,
        boolean customAlias,
        Instant expiresAt,
        Instant createdAt
) {
    public static LinkResponse from(Link link, String baseUrl, Instant now) {
        String shortUrl = baseUrl.replaceAll("/+$", "") + "/" + link.getShortCode();
        return new LinkResponse(
                link.getShortCode(),
                shortUrl,
                link.getDestinationUrl(),
                link.effectiveStatus(now),
                link.isCustomAlias(),
                link.getExpiresAt(),
                link.getCreatedAt());
    }
}
