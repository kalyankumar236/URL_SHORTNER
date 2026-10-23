package com.agentic.platform.urlshortener.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A short link mapping a short code to a destination URL. Never exposed directly
 * over the API — see {@code dto} package for response shapes.
 */
@Entity
@Table(name = "link")
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true, length = 64)
    private String shortCode;

    @Column(name = "destination_url", nullable = false, length = 2048)
    private String destinationUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LinkStatus status = LinkStatus.ACTIVE;

    @Column(name = "custom_alias", nullable = false)
    private boolean customAlias = false;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "created_by", length = 128)
    private String createdBy;

    protected Link() {
    }

    public Link(String shortCode, String destinationUrl, boolean customAlias, Instant expiresAt, String createdBy) {
        this.shortCode = shortCode;
        this.destinationUrl = destinationUrl;
        this.customAlias = customAlias;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    /** True when an expiry is set and already in the past. */
    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    /** Resolve the effective status considering expiry at the given instant. */
    public LinkStatus effectiveStatus(Instant now) {
        if (status == LinkStatus.ACTIVE && isExpired(now)) {
            return LinkStatus.EXPIRED;
        }
        return status;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getShortCode() { return shortCode; }
    public String getDestinationUrl() { return destinationUrl; }
    public LinkStatus getStatus() { return status; }
    public void setStatus(LinkStatus status) { this.status = status; }
    public boolean isCustomAlias() { return customAlias; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
}
