package com.example.project2.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.response.ProductPricingBatchItemResponse;
import com.example.project2.dto.response.ProductPricingResponse;
import com.example.project2.service.ProductPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#endpoints
 * (these two endpoints are the ones quoted in that section)
 *
 * <p>Both methods return the future itself instead of joining it. Spring MVC releases the servlet
 * thread and only writes the response once the future completes, so a slow shipping partner ties up
 * no Tomcat thread.
 *
 * <p>A failed future reaches {@code GlobalExceptionHandler} because Spring unwraps one level of
 * {@code CompletionException} first, and the service makes sure that level holds a
 * {@code BusinessException}.
 */
@RestController
@RequestMapping("/api/v1/catalog/pricing")
@RequiredArgsConstructor
public class ProductPricingController {

    private final ProductPricingService productPricingService;

    @GetMapping("/products/{productId}")
    public CompletableFuture<ApiResponse<ProductPricingResponse>> pricing(@PathVariable Long productId) {
        return productPricingService.pricing(productId).thenApply(ApiResponse::ok);
    }

    /**
     * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#fan-in-with-allof
     * The batch call. The id list is capped by catalog.async.max-batch-size, because each id costs
     * five stages and four database round trips.
     */
    @GetMapping("/products")
    public CompletableFuture<ApiResponse<List<ProductPricingBatchItemResponse>>> pricingBatch(
            @RequestParam("ids") List<Long> ids) {
        return productPricingService.pricingBatch(ids).thenApply(ApiResponse::ok);
    }
}
