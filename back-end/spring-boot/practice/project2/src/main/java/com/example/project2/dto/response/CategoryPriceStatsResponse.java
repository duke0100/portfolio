package com.example.project2.dto.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#the-pipeline
 * The result of merging the count query and the average-price query with {@code thenCombine}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryPriceStatsResponse {

    private Long categoryId;
    private Long productCount;

    /** Null when the category is empty or the product has no category. */
    private BigDecimal averagePrice;

    /** How far this product sits above (+) or below (-) its category average, in percent. */
    private BigDecimal priceVsAveragePercent;
}
