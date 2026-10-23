package com.agentic.platform.common;

import java.util.List;

/**
 * Generic pagination envelope used by list endpoints so clients get consistent
 * pagination metadata without exposing persistence internals.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
