package com.example.project2.service;

import com.example.project2.dto.response.ProductPriceAuditResponse;
import com.example.project2.event.ProductPriceChanged;

import java.util.List;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#after-commit-listener
 * Writes and reads the price-audit trail.
 *
 * <p>This is a separate bean from the listener on purpose. The listener is the {@code @Async} hop
 * and this is the {@code @Transactional} hop, so neither annotation has to sit on a self-call.
 */
public interface ProductPriceAuditService {

    /** Writes one audit row in its own transaction, on whatever thread calls it. */
    void record(ProductPriceChanged event);

    /** Newest audit rows for a product, capped by {@code catalog.async.audit-history-limit}. */
    List<ProductPriceAuditResponse> recentChanges(Long productId);
}
