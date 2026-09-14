package com.example.project2.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#endpoints
 * One audit row as the API shows it. {@code workerThread} is in the payload on purpose - it is the
 * proof that the write happened on the async pool and not on the request thread.
 */
public record ProductPriceAuditResponse(
        Long id,
        Long productId,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        String changedBy,
        String correlationId,
        String workerThread,
        Instant recordedAt) {
}
