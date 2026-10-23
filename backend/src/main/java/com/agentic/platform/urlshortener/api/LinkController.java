package com.agentic.platform.urlshortener.api;

import com.agentic.platform.common.AppProperties;
import com.agentic.platform.common.PageResponse;
import com.agentic.platform.urlshortener.domain.Link;
import com.agentic.platform.urlshortener.dto.CreateLinkRequest;
import com.agentic.platform.urlshortener.dto.LinkResponse;
import com.agentic.platform.urlshortener.service.LinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;

/**
 * Public + admin link management API.
 */
@RestController
@RequestMapping("/api/v1/links")
@Tag(name = "Links", description = "Create and manage short links")
public class LinkController {

    private final LinkService linkService;
    private final AppProperties props;

    public LinkController(LinkService linkService, AppProperties props) {
        this.linkService = linkService;
        this.props = props;
    }

    @PostMapping
    @Operation(summary = "Create a short link")
    public ResponseEntity<LinkResponse> create(
            @Valid @RequestBody CreateLinkRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {
        String createdBy = auth != null ? auth.getName() : "anonymous";
        Link link = linkService.create(request, createdBy, idempotencyKey);
        LinkResponse body = LinkResponse.from(link, props.getShort().getBaseUrl(), Instant.now());
        URI location = UriComponentsBuilder.fromUriString(props.getShort().getBaseUrl())
                .path("/{code}").build(link.getShortCode());
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get link metadata by code")
    public LinkResponse get(@PathVariable String code) {
        Link link = linkService.getByCode(code);
        return LinkResponse.from(link, props.getShort().getBaseUrl(), Instant.now());
    }

    @GetMapping
    @Operation(summary = "List links with pagination and sorting")
    public PageResponse<LinkResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = switch (sort) {
            case "shortCode", "expiresAt", "status", "createdAt" -> sort;
            default -> "createdAt";
        };
        Page<Link> result = linkService.list(PageRequest.of(Math.max(page, 0), boundedSize, Sort.by(dir, sortField)));
        Instant now = Instant.now();
        return PageResponse.of(result.map(l -> LinkResponse.from(l, props.getShort().getBaseUrl(), now)));
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "Disable a link (soft delete, preserves analytics)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String code) {
        linkService.disable(code);
    }
}
