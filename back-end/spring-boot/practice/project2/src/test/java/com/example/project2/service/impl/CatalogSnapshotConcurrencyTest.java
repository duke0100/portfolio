package com.example.project2.service.impl;

import com.example.project2.concurrent.CatalogTaskExecutor;
import com.example.project2.dto.response.CatalogSnapshotResponse;
import com.example.project2.entity.Category;
import com.example.project2.entity.Product;
import com.example.project2.repository.CategoryRepository;
import com.example.project2.repository.ProductRepository;
import com.example.project2.service.CatalogSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#under-load
 * (this is the concurrency test quoted in that section)
 *
 * <p>24 callers hit the fan-out at the same moment, which is 72 tasks through a gate of 2. The
 * invariant is that every caller still gets a complete, correct snapshot, and that no more than
 * two tasks ever touch the database at once.
 *
 * <p>The callers themselves run on virtual threads, so the test creates 24 of them without
 * creating 24 OS threads.
 *
 * <p>The tests run on H2, where these three queries are far faster than on Postgres with ~1M rows.
 * The timeout is raised here so the test asserts correctness rather than timing.
 */
@SpringBootTest(properties = {
        "catalog.snapshot.max-concurrent-tasks=2",
        "catalog.snapshot.max-in-flight-tasks=128",
        "catalog.snapshot.permit-timeout=PT20S",
        "catalog.snapshot.timeout=PT20S"
})
class CatalogSnapshotConcurrencyTest {

    private static final int CALLERS = 24;
    private static final int PRODUCTS = 30;
    private static final int LOW_STOCK_PRODUCTS = 5;
    private static final int MAX_CONCURRENT_TASKS = 2;

    @Autowired
    private CatalogSnapshotService catalogSnapshotService;

    @Autowired
    private CatalogTaskExecutor catalogTaskExecutor;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void concurrentCallers_allGetTheSameCompleteSnapshot() throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .name("Fan-out test category")
                .build());
        seedProducts(category);

        ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<CatalogSnapshotResponse>> results = new ArrayList<>();
        try {
            for (int i = 0; i < CALLERS; i++) {
                results.add(callers.submit(() -> {
                    startGun.await(); // every thread waits here, then they all go at once
                    return catalogSnapshotService.snapshot(category.getId());
                }));
            }
            startGun.countDown();

            for (Future<CatalogSnapshotResponse> result : results) {
                CatalogSnapshotResponse snapshot = result.get(60, TimeUnit.SECONDS);
                assertThat(snapshot.isPartial()).isFalse();
                assertThat(snapshot.getProductCount()).isEqualTo(PRODUCTS);
                assertThat(snapshot.getLowStockProducts()).hasSize(LOW_STOCK_PRODUCTS);
                // Prices run 100.00 to 129.00, so the average is fixed no matter who wins the race.
                assertThat(snapshot.getAveragePrice()).isEqualByComparingTo("114.50");
            }
        } finally {
            callers.shutdownNow();
        }
    }

    /**
     * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#limiting-an-unbounded-executor
     * The executor would happily start 100 virtual threads at once. The semaphore is what keeps
     * only two of them inside the task body, which is what protects the connection pool.
     */
    @Test
    void permitGate_neverLetsMoreTasksRunThanPermits() throws Exception {
        int tasks = 100; // under max-in-flight-tasks=128, so nothing is rejected here
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch finished = new CountDownLatch(tasks);

        List<Future<Integer>> submitted = new ArrayList<>();
        for (int i = 0; i < tasks; i++) {
            submitted.add(catalogTaskExecutor.submit(() -> {
                int now = inside.incrementAndGet();
                peak.accumulateAndGet(now, Math::max);
                try {
                    // Long enough that a broken gate would overlap and be caught.
                    Thread.sleep(5);
                    return now;
                } finally {
                    inside.decrementAndGet();
                    finished.countDown();
                }
            }));
        }

        assertThat(finished.await(60, TimeUnit.SECONDS)).isTrue();
        for (Future<Integer> future : submitted) {
            assertThat(future.get(10, TimeUnit.SECONDS)).isPositive();
        }
        assertThat(peak.get()).isLessThanOrEqualTo(MAX_CONCURRENT_TASKS);
    }

    /** Five products sit on or below their low-stock threshold, the other 25 are well stocked. */
    private void seedProducts(Category category) {
        for (int i = 0; i < PRODUCTS; i++) {
            productRepository.save(Product.builder()
                    .category(category)
                    .name("Fan-out probe " + i)
                    .sku("FANOUT-" + i)
                    .price(BigDecimal.valueOf(100 + i))
                    .stockQuantity(i < LOW_STOCK_PRODUCTS ? 1 : 500)
                    .lowStockThreshold(5)
                    .build());
        }
    }
}
