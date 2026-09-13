package com.example.project2.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#callable-fan-out
 * The result of the three Callables, merged into one response.
 *
 * <p>{@code partial} is true when a task missed the timeout. The client gets a 200 with the parts
 * that arrived, and can tell that a number is missing rather than zero.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogSnapshotResponse {

    private Long categoryId;
    private String categoryName;

    /** Null when the counting task missed the timeout. */
    private Long productCount;

    /** Null when the average-price task missed the timeout. */
    private BigDecimal averagePrice;

    /** Empty when the low-stock task missed the timeout, capped at catalog.snapshot.low-stock-limit. */
    private List<ProductSummaryResponse> lowStockProducts;

    private boolean partial;
}
