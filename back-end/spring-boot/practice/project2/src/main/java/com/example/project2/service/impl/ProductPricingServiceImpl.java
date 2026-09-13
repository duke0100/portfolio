package com.example.project2.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project2.client.ShippingQuoteClient;
import com.example.project2.config.CatalogAsyncProperties;
import com.example.project2.dto.response.CategoryPriceStatsResponse;
import com.example.project2.dto.response.PricedProductResponse;
import com.example.project2.dto.response.ProductPricingBatchItemResponse;
import com.example.project2.dto.response.ProductPricingResponse;
import com.example.project2.dto.response.ProductSummaryResponse;
import com.example.project2.dto.response.ShippingQuoteResponse;
import com.example.project2.repository.ProductRepository;
import com.example.project2.service.ProductPricingService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#the-pipeline
 * (this class is the chaining code sample in that section)
 *
 * <p>Five stages run as one non-blocking chain: product, category count, category average, related
 * products, shipping quote. Nothing here calls {@code get()} or {@code join()} on a stage that is
 * still running, so no pool thread is ever parked waiting for another pool thread.
 *
 * <p>Not {@code @Transactional}: a transaction lives in a ThreadLocal and does not follow a stage
 * onto a pool thread. Each stage opens its own short read transaction through the repository.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductPricingServiceImpl implements ProductPricingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ProductRepository productRepository;
    private final ShippingQuoteClient shippingQuoteClient;
    private final CatalogAsyncProperties properties;
    private final ThreadPoolTaskExecutor catalogAsyncExecutor;
    private final MeterRegistry meterRegistry;

    /**
     * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#the-pipeline
     * The chain itself. Every async call is handed our own executor, because the default is
     * {@code ForkJoinPool.commonPool()} and these stages block on the database.
     */
    @Override
    public CompletableFuture<ProductPricingResponse> pricing(Long productId) {
        long startedAt = System.nanoTime();

        // Stage 1. supplyAsync starts the chain on our pool, not on the request thread.
        CompletableFuture<PricedProductResponse> productFuture =
                CompletableFuture.supplyAsync(() -> loadProduct(productId), catalogAsyncExecutor);

        // Stages 2+3. thenCompose, because categoryStats returns a future of its own. thenApply
        // here would give CompletableFuture<CompletableFuture<CategoryPriceStatsResponse>>.
        CompletableFuture<CategoryPriceStatsResponse> statsFuture =
                productFuture.thenCompose(this::categoryStats);

        // Stage 4. thenApplyAsync, not thenApply: this one runs a query and must not run inline on
        // whichever thread happened to complete stage 1.
        CompletableFuture<List<ProductSummaryResponse>> relatedFuture =
                productFuture.thenApplyAsync(this::relatedProducts, catalogAsyncExecutor);

        // Stage 5. Independent of stages 2 to 4, so it runs at the same time as them.
        CompletableFuture<ShippingQuoteResponse> shippingFuture =
                productFuture.thenCompose(this::shippingQuote);

        return CompletableFuture.allOf(statsFuture, relatedFuture, shippingFuture)
                .thenApply(ignored -> {
                    // join() here cannot block: allOf only completes once all three are done.
                    ShippingQuoteResponse shipping = shippingFuture.join();
                    return ProductPricingResponse.builder()
                            .product(productFuture.join())
                            .categoryStats(statsFuture.join())
                            .relatedProducts(relatedFuture.join())
                            .shipping(shipping)
                            .degraded("FALLBACK".equals(shipping.getSource()))
                            .elapsedMs(elapsedMs(startedAt))
                            .build();
                })
                // Backstop for the whole chain. It fails the response but does not stop the stages,
                // so it has to be wider than every per-stage budget inside it.
                .orTimeout(properties.getTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .handle(this::completeOrTranslate);
    }

    /**
     * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#fan-in-with-allof
     * Each id is recovered on its own before the fan-in, so allOf can never fail here. One bad id
     * costs one error row instead of the whole batch.
     */
    @Override
    public CompletableFuture<List<ProductPricingBatchItemResponse>> pricingBatch(List<Long> productIds) {
        List<Long> ids = productIds == null ? List.of()
                : productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw BusinessException.badRequest("At least one product id is required");
        }
        if (ids.size() > properties.getMaxBatchSize()) {
            throw BusinessException.badRequest("At most " + properties.getMaxBatchSize()
                    + " ids per batch, got " + ids.size());
        }

        List<CompletableFuture<ProductPricingBatchItemResponse>> itemFutures = ids.stream()
                .map(id -> pricing(id).handle((pricing, throwable) -> batchItem(id, pricing, throwable)))
                .toList();

        return CompletableFuture.allOf(itemFutures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> itemFutures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /** The body of stage 1. A missing id fails the chain here and surfaces as a 404. */
    private PricedProductResponse loadProduct(Long productId) {
        return productRepository.findPricingProjection(productId)
                .orElseThrow(() -> BusinessException.notFound("Product not found with id: " + productId));
    }

    /**
     * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#the-pipeline
     * Two independent queries merged with thenCombine. Returning a future is what lets the caller
     * use thenCompose and keep one flat chain.
     */
    private CompletableFuture<CategoryPriceStatsResponse> categoryStats(PricedProductResponse product) {
        Long categoryId = product.getCategoryId();
        if (categoryId == null) {
            // Nothing to query. completedFuture keeps the type of the chain without touching the pool.
            return CompletableFuture.completedFuture(CategoryPriceStatsResponse.builder()
                    .productCount(0L)
                    .build());
        }
        CompletableFuture<Long> countFuture = CompletableFuture.supplyAsync(
                () -> productRepository.countByCategory(categoryId), catalogAsyncExecutor);
        CompletableFuture<BigDecimal> averageFuture = CompletableFuture.supplyAsync(
                () -> averagePrice(categoryId), catalogAsyncExecutor);

        return countFuture.thenCombine(averageFuture, (count, average) -> CategoryPriceStatsResponse.builder()
                .categoryId(categoryId)
                .productCount(count)
                .averagePrice(average)
                .priceVsAveragePercent(priceVsAverage(product.getPrice(), average))
                .build());
    }

    /** Null for an empty category, which the response reports as a null average rather than 0.00. */
    private BigDecimal averagePrice(Long categoryId) {
        Double average = productRepository.averagePriceByCategory(categoryId);
        return average == null ? null : BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal priceVsAverage(BigDecimal price, BigDecimal average) {
        if (price == null || average == null || average.signum() == 0) {
            return null;
        }
        return price.subtract(average)
                .multiply(HUNDRED)
                .divide(average, 2, RoundingMode.HALF_UP);
    }

    /** The body of stage 4, capped by the Pageable so the response size cannot follow the category size. */
    private List<ProductSummaryResponse> relatedProducts(PricedProductResponse product) {
        if (product.getCategoryId() == null) {
            return List.of();
        }
        return productRepository.findRelatedByCategory(product.getCategoryId(), product.getId(),
                PageRequest.ofSize(properties.getRelatedLimit()));
    }

    /**
     * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#timeouts-and-fallback
     * orTimeout then exceptionally, rather than completeOnTimeout: the fallback quote is only built
     * when it is really needed, so the FALLBACK counter stays honest.
     */
    private CompletableFuture<ShippingQuoteResponse> shippingQuote(PricedProductResponse product) {
        return shippingQuoteClient.quoteAsync(product.getId(), product.getWeight())
                .orTimeout(properties.getShipping().getQuoteTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> {
                    Throwable cause = rootCause(throwable);
                    log.warn("Shipping partner unavailable for product {} ({}: {}), using the flat rate",
                            product.getId(), cause.getClass().getSimpleName(), cause.getMessage());
                    return shippingQuoteClient.fallbackQuote(product.getWeight());
                });
    }

    private ProductPricingBatchItemResponse batchItem(Long productId, ProductPricingResponse pricing,
                                                      Throwable throwable) {
        return ProductPricingBatchItemResponse.builder()
                .productId(productId)
                .pricing(throwable == null ? pricing : null)
                .error(throwable == null ? null : rootCause(throwable).getMessage())
                .build();
    }

    /**
     * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#exception-handling
     * Rethrowing from handle leaves the future failing with exactly CompletionException wrapping our
     * BusinessException, which is the one level Spring MVC unwraps before the advice sees it.
     */
    private ProductPricingResponse completeOrTranslate(ProductPricingResponse response, Throwable throwable) {
        if (throwable == null) {
            return response;
        }
        throw translate(throwable);
    }

    private RuntimeException translate(Throwable throwable) {
        Throwable cause = rootCause(throwable);
        if (cause instanceof BusinessException businessException) {
            return businessException;
        }
        if (cause instanceof TimeoutException) {
            meterRegistry.counter("catalog.pricing.timeout").increment();
            return new BusinessException("Pricing timed out after " + properties.getTimeout(),
                    HttpStatus.GATEWAY_TIMEOUT, "PRICING_TIMEOUT");
        }
        log.error("Pricing pipeline failed", cause);
        return new BusinessException("Pricing failed: " + cause.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR, "PRICING_FAILED");
    }

    /**
     * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#exception-handling
     * Every stage wraps a failure in CompletionException, and nesting wraps it again. Without this
     * unwrapping the advice would see a CompletionException and answer 500 for a missing product.
     */
    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private long elapsedMs(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
