package com.example.project2.dto.response;

import java.math.BigDecimal;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#the-price-change
 * What the caller gets back the moment the price change commits.
 *
 * <p>{@code auditStatus} is deliberately "SCHEDULED": the audit row is written on another thread
 * after commit, so it is usually not there yet when this response is serialized.
 */
public record ProductPriceChangeResponse(
        Long productId,
        String productName,
        BigDecimal previousPrice,
        BigDecimal newPrice,
        String changedBy,
        String auditStatus) {
}
