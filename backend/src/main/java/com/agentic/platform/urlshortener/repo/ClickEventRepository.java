package com.agentic.platform.urlshortener.repo;

import com.agentic.platform.urlshortener.domain.ClickEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByLinkId(Long linkId);

    long countByLinkIdAndOccurredAtBetween(Long linkId, Instant from, Instant to);

    long countByOccurredAtBetween(Instant from, Instant to);

    /**
     * Events for a link within a window. Daily aggregation is computed in the
     * service layer (in Java, UTC day buckets) to stay portable across H2/PostgreSQL.
     */
    List<ClickEvent> findByLinkIdAndOccurredAtBetweenOrderByOccurredAtAsc(Long linkId, Instant from, Instant to);

    /** Top links by total clicks. Returns rows of [linkId, count]. */
    @Query("""
            select c.linkId, count(c) as total
            from ClickEvent c
            group by c.linkId
            order by total desc
            """)
    List<Object[]> topLinks(Pageable pageable);
}
