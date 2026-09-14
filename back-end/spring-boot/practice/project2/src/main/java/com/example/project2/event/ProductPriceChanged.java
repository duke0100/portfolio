package com.example.project2.event;

import java.math.BigDecimal;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#after-commit-listener
 * Published inside the price-change transaction, delivered only after it commits.
 *
 * <p>It carries values, not the {@code Product} entity. An entity handed to another thread is
 * detached there, and touching a lazy field on it throws {@code LazyInitializationException}.
 */
public record ProductPriceChanged(
        Long productId,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        String changedBy) {
}
