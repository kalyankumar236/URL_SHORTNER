package com.agentic.platform.urlshortener.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Records a processed idempotency key so retried mutating requests (same scope +
 * key) are not applied twice. Uniqueness is enforced by a DB constraint.
 */
@Entity
@Table(name = "idempotency_key")
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_value", nullable = false, length = 128)
    private String keyValue;

    @Column(nullable = false, length = 64)
    private String scope;

    @Column(name = "response_ref", length = 128)
    private String responseRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected IdempotencyKey() {
    }

    public IdempotencyKey(String scope, String keyValue, String responseRef) {
        this.scope = scope;
        this.keyValue = keyValue;
        this.responseRef = responseRef;
    }

    public Long getId() { return id; }
    public String getKeyValue() { return keyValue; }
    public String getScope() { return scope; }
    public String getResponseRef() { return responseRef; }
    public Instant getCreatedAt() { return createdAt; }
}
