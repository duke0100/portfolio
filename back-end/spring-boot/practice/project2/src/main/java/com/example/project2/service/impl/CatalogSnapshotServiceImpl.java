package com.example.project2.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project2.concurrent.CatalogTaskExecutor;
import com.example.project2.config.CatalogSnapshotProperties;
import com.example.project2.dto.response.CatalogSnapshotResponse;
import com.example.project2.dto.response.ProductSummaryResponse;
import com.example.project2.entity.Category;
import com.example.project2.repository.CategoryRepository;
import com.example.project2.repository.ProductRepository;
import com.example.project2.service.CatalogSnapshotService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#callable-fan-out
 * (this class is the Callable + Future code sample in that section, and the Runnable sample in
 * #runnable-fire-and-forget)
 *
 * <p>Three independent read queries run at the same time instead of one after another, so the
 * endpoint costs about one query of latency rather than three.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogSnapshotServiceImpl implements CatalogSnapshotService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CatalogTaskExecutor catalogTaskExecutor;
    private final CatalogSnapshotProperties properties;
    private final MeterRegistry meterRegistry;

    /**
     * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#callable-fan-out
     * Deliberately not {@code @Transactional}: a transaction lives in a ThreadLocal and does not
     * follow a task onto another thread. The caller would also sit on a connection while it waits.
     */
    @Override
    public CatalogSnapshotResponse snapshot(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> BusinessException.notFound("Category not found with id: " + categoryId));

        // Each Callable opens its own transaction and its own connection on its own virtual thread.
        Callable<Long> countTask = () -> productRepository.countByCategory(categoryId);
        Callable<BigDecimal> averagePriceTask = () -> {
            Double average = productRepository.averagePriceByCategory(categoryId);
            // An empty category has no average, so the response says 0.00 instead of null.
            return average == null
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP);
        };
        Callable<List<ProductSummaryResponse>> lowStockTask = () ->
                productRepository.findLowStockByCategory(categoryId, PageRequest.ofSize(properties.getLowStockLimit()));

        // A rejection here means the limiter's waiting room is full. Fail fast with 503 instead of
        // making the caller wait behind work the executor has no room for.
        Future<Long> countFuture;
        Future<BigDecimal> averagePriceFuture;
        Future<List<ProductSummaryResponse>> lowStockFuture;
        try {
            countFuture = catalogTaskExecutor.submit(countTask);
            averagePriceFuture = catalogTaskExecutor.submit(averagePriceTask);
            lowStockFuture = catalogTaskExecutor.submit(lowStockTask);
        } catch (RejectedExecutionException e) {
            throw busy(e);
        }

        // One budget for the whole fan-out, not one per task.
        long deadline = System.nanoTime() + properties.getTimeout().toNanos();
        Optional<Long> count = await(countFuture, deadline, "productCount");
        Optional<BigDecimal> averagePrice = await(averagePriceFuture, deadline, "averagePrice");
        Optional<List<ProductSummaryResponse>> lowStock = await(lowStockFuture, deadline, "lowStock");

        /*
         * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#runnable-fire-and-forget
         * (this call is the Runnable sample there) Telemetry has no result the caller needs, so it
         * is a Runnable and nobody waits for it.
         */
        catalogTaskExecutor.execute(() -> recordSnapshotServed(categoryId));

        return CatalogSnapshotResponse.builder()
                .categoryId(category.getId())
                .categoryName(category.getName())
                .productCount(count.orElse(null))
                .averagePrice(averagePrice.orElse(null))
                .lowStockProducts(lowStock.orElseGet(List::of))
                .partial(count.isEmpty() || averagePrice.isEmpty() || lowStock.isEmpty())
                .build();
    }

    /**
     * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#reading-the-future
     * Waits for one task until the shared deadline. A slow part is dropped so the endpoint still
     * answers, and a failed part is rethrown so the caller gets a real error.
     */
    private <T> Optional<T> await(Future<T> future, long deadlineNanos, String part) {
        try {
            long remaining = Math.max(0, deadlineNanos - System.nanoTime());
            return Optional.ofNullable(future.get(remaining, TimeUnit.NANOSECONDS));
        } catch (TimeoutException e) {
            // Cancel with interrupt, otherwise the task keeps a connection busy for nothing.
            future.cancel(true);
            meterRegistry.counter("catalog.snapshot.part.timeout", "part", part).increment();
            log.warn("Catalog snapshot part '{}' missed the {} budget, answering partially",
                    part, properties.getTimeout());
            return Optional.empty();
        } catch (ExecutionException e) {
            throw asRuntime(e.getCause(), part);
        } catch (InterruptedException e) {
            // The calling thread was interrupted: restore the flag, then fail fast.
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new BusinessException("Catalog snapshot was interrupted",
                    HttpStatus.SERVICE_UNAVAILABLE, "SNAPSHOT_INTERRUPTED");
        }
    }

    /** Keeps the original exception when it already carries a status, so the advice still maps it. */
    private RuntimeException asRuntime(Throwable cause, String part) {
        // The task gave up waiting for a database slot. That is overload, not a bug, so say 503.
        if (cause instanceof RejectedExecutionException rejected) {
            return busy(rejected);
        }
        if (cause instanceof RuntimeException runtimeCause) {
            return runtimeCause;
        }
        return new BusinessException("Catalog snapshot part '" + part + "' failed: " + cause.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR, "SNAPSHOT_FAILED");
    }

    /**
     * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#limiting-an-unbounded-executor
     * Turns a limiter rejection into the 503 that GlobalExceptionHandler renders as an ApiResponse.
     */
    private BusinessException busy(RejectedExecutionException cause) {
        meterRegistry.counter("catalog.snapshot.rejected").increment();
        log.warn("Catalog snapshot shed load: {}", cause.getMessage());
        return new BusinessException("Catalog snapshot is busy, retry shortly",
                HttpStatus.SERVICE_UNAVAILABLE, "SNAPSHOT_BUSY");
    }

    /**
     * The fire-and-forget body. There is no categoryId tag on purpose, because a tag value the
     * caller controls creates one time series per value.
     */
    private void recordSnapshotServed(Long categoryId) {
        meterRegistry.counter("catalog.snapshot.served").increment();
        log.info("Catalog snapshot served for category {}", categoryId);
    }
}
