package com.agentic.platform.urlshortener.service;

import com.agentic.platform.common.ApiException;
import com.agentic.platform.common.AppProperties;
import com.agentic.platform.urlshortener.domain.IdempotencyKey;
import com.agentic.platform.urlshortener.domain.Link;
import com.agentic.platform.urlshortener.domain.LinkStatus;
import com.agentic.platform.urlshortener.dto.CreateLinkRequest;
import com.agentic.platform.urlshortener.repo.IdempotencyKeyRepository;
import com.agentic.platform.urlshortener.repo.LinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Core URL shortener operations: collision-safe creation, lookup, listing, soft
 * delete (disable), and redirect resolution. Treats input as untrusted.
 */
@Service
public class LinkService {

    private static final Logger log = LoggerFactory.getLogger(LinkService.class);
    private static final int MAX_CODE_ATTEMPTS = 6;
    private static final String IDEMPOTENCY_SCOPE = "create-link";

    /** Reserved aliases that must not collide with application routes. */
    private static final Set<String> RESERVED = Set.of(
            "api", "admin", "actuator", "swagger-ui", "v3", "h2-console", "health",
            "metrics", "links", "analytics", "workflows", "approvals", "agents", "policies", "favicon.ico");

    private final LinkRepository linkRepo;
    private final IdempotencyKeyRepository idempotencyRepo;
    private final ShortCodeGenerator codeGenerator;
    private final UrlValidator urlValidator;
    private final AppProperties props;

    public LinkService(LinkRepository linkRepo, IdempotencyKeyRepository idempotencyRepo,
                       ShortCodeGenerator codeGenerator, UrlValidator urlValidator, AppProperties props) {
        this.linkRepo = linkRepo;
        this.idempotencyRepo = idempotencyRepo;
        this.codeGenerator = codeGenerator;
        this.urlValidator = urlValidator;
        this.props = props;
    }

    @Transactional
    public Link create(CreateLinkRequest request, String createdBy, String idempotencyKey) {
        // Idempotency: a repeated request with the same key returns the original link.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyKey> existing = idempotencyRepo.findByScopeAndKeyValue(IDEMPOTENCY_SCOPE, idempotencyKey);
            if (existing.isPresent()) {
                return linkRepo.findByShortCode(existing.get().getResponseRef())
                        .orElseThrow(() -> ApiException.conflict("Idempotency key refers to a missing link"));
            }
        }

        String destination = urlValidator.validateAndNormalize(request.destinationUrl());
        validateExpiry(request.expiresAt());

        Link saved;
        if (request.alias() != null && !request.alias().isBlank()) {
            saved = createWithAlias(request.alias(), destination, request.expiresAt(), createdBy);
        } else {
            saved = createWithGeneratedCode(destination, request.expiresAt(), createdBy);
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                idempotencyRepo.save(new IdempotencyKey(IDEMPOTENCY_SCOPE, idempotencyKey, saved.getShortCode()));
            } catch (DataIntegrityViolationException race) {
                // Concurrent duplicate; the other writer's link is authoritative.
                return idempotencyRepo.findByScopeAndKeyValue(IDEMPOTENCY_SCOPE, idempotencyKey)
                        .flatMap(k -> linkRepo.findByShortCode(k.getResponseRef()))
                        .orElse(saved);
            }
        }
        return saved;
    }

    private Link createWithAlias(String alias, String destination, Instant expiresAt, String createdBy) {
        String normalizedAlias = alias.trim();
        if (normalizedAlias.length() > props.getShort().getMaxAliasLength()) {
            throw ApiException.badRequest("alias exceeds maximum length");
        }
        if (RESERVED.contains(normalizedAlias.toLowerCase(Locale.ROOT))) {
            throw ApiException.conflict("alias is reserved");
        }
        if (linkRepo.existsByShortCode(normalizedAlias)) {
            throw ApiException.conflict("alias already in use");
        }
        try {
            return linkRepo.save(new Link(normalizedAlias, destination, true, expiresAt, createdBy));
        } catch (DataIntegrityViolationException e) {
            // Lost the race to a concurrent creator using the same alias.
            throw ApiException.conflict("alias already in use");
        }
    }

    private Link createWithGeneratedCode(String destination, Instant expiresAt, String createdBy) {
        int length = props.getShort().getCodeLength();
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate(length);
            try {
                return linkRepo.save(new Link(code, destination, false, expiresAt, createdBy));
            } catch (DataIntegrityViolationException collision) {
                log.debug("Short-code collision on '{}', retrying ({})", code, attempt + 1);
            }
        }
        throw ApiException.conflict("Unable to allocate a unique short code, please retry");
    }

    private void validateExpiry(Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw ApiException.badRequest("expiresAt must be in the future");
        }
    }

    @Transactional(readOnly = true)
    public Link getByCode(String code) {
        return linkRepo.findByShortCode(code)
                .orElseThrow(() -> ApiException.notFound("No link found for code: " + code));
    }

    @Transactional(readOnly = true)
    public Page<Link> list(Pageable pageable) {
        return linkRepo.findAll(pageable);
    }

    /** Soft delete: disables the link (preserves analytics, reversible). */
    @Transactional
    public void disable(String code) {
        Link link = getByCode(code);
        if (link.getStatus() != LinkStatus.DISABLED) {
            link.setStatus(LinkStatus.DISABLED);
            linkRepo.save(link);
        }
    }

    /**
     * Resolve a code for redirect. Returns the active link or throws:
     * 404 for unknown, 410 (GONE) for expired/disabled.
     */
    @Transactional(readOnly = true)
    public Link resolveForRedirect(String code) {
        Link link = linkRepo.findByShortCode(code)
                .orElseThrow(() -> ApiException.notFound("Short link not found"));
        Instant now = Instant.now();
        LinkStatus effective = link.effectiveStatus(now);
        if (effective == LinkStatus.DISABLED) {
            throw ApiException.gone("This short link has been disabled");
        }
        if (effective == LinkStatus.EXPIRED) {
            throw ApiException.gone("This short link has expired");
        }
        return link;
    }
}
