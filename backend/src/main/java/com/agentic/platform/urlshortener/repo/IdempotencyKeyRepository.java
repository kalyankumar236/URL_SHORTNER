package com.agentic.platform.urlshortener.repo;

import com.agentic.platform.urlshortener.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
    Optional<IdempotencyKey> findByScopeAndKeyValue(String scope, String keyValue);
}
