package com.example.project2.service.impl;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#under-load
 * (this is the concurrency test quoted in that section)
 *
 * <p>24 callers hit the fan-out at the same moment, which is 72 tasks on a 2-thread pool with a
 * queue of 8. The invariant is that every caller still gets a complete, correct snapshot: nothing
 * is rejected, because a full queue makes the caller run the task itself.
 *
 * <p>The tests run on H2, where these three queries are far faster than on Postgres with ~1M rows.
 * The timeout is raised here so the test asserts correctness rather than timing.
 */
@SpringBootTest(properties = {
        "catalog.snapshot.pool-size=2",
        "catalog.snapshot.queue-capacity=8",
        "catalog.snapshot.timeout=PT20S"
})
class CatalogSnapshotConcurrencyTest {

    private static final int CALLERS = 24;
    private static final int PRODUCTS = 30;
    private static final int LOW_STOCK_PRODUCTS = 5;

    @Autowired
    private CatalogSnapshotService catalogSnapshotService;

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

        ExecutorService callers = Executors.newFixedThreadPool(CALLERS);
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
