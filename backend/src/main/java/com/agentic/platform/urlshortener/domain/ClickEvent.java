package com.agentic.platform.urlshortener.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A single redirect event. Privacy-conscious: raw IPs are never stored — only a
 * salted, truncated client hash, the referrer host, and a coarse user-agent class.
 */
@Entity
@Table(name = "click_event")
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "client_hash", length = 64)
    private String clientHash;

    @Column(name = "referrer_host", length = 255)
    private String referrerHost;

    @Column(name = "ua_class", length = 32)
    private String uaClass;

    protected ClickEvent() {
    }

    public ClickEvent(Long linkId, Instant occurredAt, int responseStatus,
                      String clientHash, String referrerHost, String uaClass) {
        this.linkId = linkId;
        this.occurredAt = occurredAt;
        this.responseStatus = responseStatus;
        this.clientHash = clientHash;
        this.referrerHost = referrerHost;
        this.uaClass = uaClass;
    }

    public Long getId() { return id; }
    public Long getLinkId() { return linkId; }
    public Instant getOccurredAt() { return occurredAt; }
    public int getResponseStatus() { return responseStatus; }
    public String getClientHash() { return clientHash; }
    public String getReferrerHost() { return referrerHost; }
    public String getUaClass() { return uaClass; }
}
