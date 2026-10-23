package com.agentic.platform.common;

import java.time.Instant;
import java.util.List;

/**
 * Consistent API error contract returned for all handled failures.
 *
 * @param timestamp   when the error was produced (UTC)
 * @param status      HTTP status code
 * @param error       short error code (machine-friendly)
 * @param message     human-readable description (safe to display, no secrets)
 * @param path        request path
 * @param correlationId correlation id for cross-referencing logs
 * @param details     optional field-level validation messages
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<String> details
) {
    public static ApiError of(int status, String error, String message, String path, String correlationId, List<String> details) {
        return new ApiError(Instant.now(), status, error, message, path, correlationId, details == null ? List.of() : details);
    }
}
