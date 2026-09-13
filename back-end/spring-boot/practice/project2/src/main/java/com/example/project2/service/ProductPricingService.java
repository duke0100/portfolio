package com.example.project2.service;

import com.example.project2.dto.response.ProductPricingBatchItemResponse;
import com.example.project2.dto.response.ProductPricingResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#the-pipeline
 * The async pricing pipeline. Both methods return a future rather than a value, so the caller -
 * including Spring MVC - decides when, or whether, to block.
 */
public interface ProductPricingService {

    /**
     * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#the-pipeline
     * Product, category stats, related products and a shipping quote, gathered in parallel.
     *
     * <p>The future fails with a {@code BusinessException} cause for an unknown id or a blown
     * budget. It never fails with a bare {@code CompletionException}.
     */
    CompletableFuture<ProductPricingResponse> pricing(Long productId);

    /**
     * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#fan-in-with-allof
     * The same pipeline for several ids at once. One failing id comes back as an error row instead
     * of failing the whole batch.
     */
    CompletableFuture<List<ProductPricingBatchItemResponse>> pricingBatch(List<Long> productIds);
}
