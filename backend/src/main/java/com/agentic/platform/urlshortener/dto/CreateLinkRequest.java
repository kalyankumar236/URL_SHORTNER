package com.agentic.platform.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request to create a short link. Validation here is the first (syntactic) layer;
 * semantic checks (scheme allowlist, alias reservations) happen in the service.
 */
public record CreateLinkRequest(
        @NotBlank(message = "destinationUrl is required")
        @Size(max = 2048, message = "destinationUrl exceeds maximum length")
        String destinationUrl,

        @Size(max = 32, message = "alias exceeds maximum length")
        @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "alias may only contain letters, digits, '-' and '_'")
        String alias,

        Instant expiresAt
) {
}
