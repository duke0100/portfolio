package com.example.project2.dto.response;

import lombok.*;

import java.util.List;

/**
 * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#endpoints
 * Everything the five pipeline stages produced, merged in the {@code allOf} fan-in.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPricingResponse {

    private PricedProductResponse product;
    private CategoryPriceStatsResponse categoryStats;

    /** Capped at catalog.async.related-limit rows. */
    private List<ProductSummaryResponse> relatedProducts;

    private ShippingQuoteResponse shipping;

    /** True when at least one part fell back, so the caller knows the answer is not fully live. */
    private boolean degraded;

    /** Wall-clock time of the whole pipeline. Close to the slowest stage, not the sum of them. */
    private long elapsedMs;
}
