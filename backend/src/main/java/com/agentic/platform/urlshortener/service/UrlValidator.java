package com.agentic.platform.urlshortener.service;

import com.agentic.platform.common.ApiException;
import com.agentic.platform.common.AppProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Validates and safely normalizes destination URLs. Treats URLs as untrusted input.
 *
 * <p>Security rules:
 * <ul>
 *   <li>Only {@code http} and {@code https} schemes are accepted.</li>
 *   <li>Dangerous schemes (javascript:, data:, file:, etc.) are rejected.</li>
 *   <li>A host must be present.</li>
 *   <li>Length is bounded.</li>
 * </ul>
 * Normalization is conservative: it lower-cases scheme and host only, and never
 * rewrites the path/query/fragment so the destination meaning is preserved. The
 * server never fetches the destination to validate it (avoids SSRF).
 */
@Component
public class UrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final AppProperties props;

    public UrlValidator(AppProperties props) {
        this.props = props;
    }

    /** Validate and return a conservatively normalized URL, or throw {@link ApiException}. */
    public String validateAndNormalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("destinationUrl is required");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > props.getShort().getMaxUrlLength()) {
            throw ApiException.badRequest("destinationUrl exceeds maximum length");
        }

        final URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw ApiException.badRequest("destinationUrl is not a valid URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw ApiException.badRequest("destinationUrl must include a scheme (http or https)");
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw ApiException.badRequest("Unsupported URL scheme: only http and https are allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw ApiException.badRequest("destinationUrl must include a host");
        }

        // Reconstruct with lower-cased scheme + host, preserving everything else verbatim.
        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://");
        if (uri.getUserInfo() != null) {
            sb.append(uri.getUserInfo()).append('@');
        }
        sb.append(host.toLowerCase(Locale.ROOT));
        if (uri.getPort() != -1) {
            sb.append(':').append(uri.getPort());
        }
        if (uri.getRawPath() != null) {
            sb.append(uri.getRawPath());
        }
        if (uri.getRawQuery() != null) {
            sb.append('?').append(uri.getRawQuery());
        }
        if (uri.getRawFragment() != null) {
            sb.append('#').append(uri.getRawFragment());
        }
        return sb.toString();
    }
}
