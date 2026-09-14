package com.example.project2.repository;

import com.example.project2.entity.ProductPriceAudit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#after-commit-listener
 * Reads and writes the price-audit trail the async listener produces.
 */
public interface ProductPriceAuditRepository extends JpaRepository<ProductPriceAudit, Long> {

    /**
     * Newest audit rows for one product. The Pageable is the bound - a product edited every minute
     * for a year would otherwise return half a million rows.
     */
    List<ProductPriceAudit> findByProductIdOrderByRecordedAtDescIdDesc(Long productId, Pageable pageable);

    /** Used by the concurrency test to assert exactly one audit row per committed change. */
    long countByProductId(Long productId);
}
