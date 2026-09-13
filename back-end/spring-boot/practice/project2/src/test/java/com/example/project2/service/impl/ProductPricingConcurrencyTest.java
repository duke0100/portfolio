package com.example.project2.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project2.dto.response.ProductPricingBatchItemResponse;
import com.example.project2.dto.response.ProductPricingResponse;
import com.example.project2.entity.Category;
import com.example.project2.entity.Product;
import com.example.project2.repository.CategoryRepository;
import com.example.project2.repository.ProductRepository;
import com.example.project2.service.ProductPricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#under-load
 * (this is the concurrency test quoted in that section)
 *
 * <p>24 callers start the pipeline at the same moment, which is 120 stages on a 2-thread pool with
 * a queue of 8. The invariant is that every caller gets the same correct answer: nothing is
 * rejected, nothing deadlocks, and the shipping fallback fires exactly once per caller.
 *
 * <p>A pool this small is also the deadlock check. The chain never blocks a stage on another
 * stage, so two threads are enough; a {@code join()} inside a stage would hang this test instead.
 *
 * <p>These tests run on H2, where the four queries are far faster than on Postgres with ~1M rows.
 * The pipeline budget is raised here so the test asserts correctness rather than timing.
 */
@SpringBootTest(properties = {
        "catalog.async.pool-size=2",
        "catalog.async.queue-capacity=8",
        "catalog.async.timeout=PT60S",
        "catalog.async.related-limit=5",
        // Nothing listens on this port, so every caller takes the fallback branch.
        "catalog.async.shipping.base-url=http://localhost:9099",
        "catalog.async.shipping.quote-timeout=PT5S"
})
class ProductPricingConcurrencyTest {

    private static final int CALLERS = 24;
    private static final int PRODUCTS = 8;
    private static final int RELATED_LIMIT = 5;

    @Autowired
    private ProductPricingService productPricingService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    private Long productId;

    @BeforeEach
    void seedCatalog() {
        Category category = categoryRepository.save(Category.builder()
                .name("Pricing pipeline category")
                .build());
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < PRODUCTS; i++) {
            products.add(productRepository.save(Product.builder()
                    .category(category)
                    .name("Pricing probe " + i)
                    .sku("PRICING-" + System.nanoTime() + "-" + i)
                    .price(BigDecimal.valueOf(100 + i))
                    .weight(new BigDecimal("2.000"))
                    .stockQuantity(50)
                    .lowStockThreshold(5)
                    .build()));
        }
        productId = products.getFirst().getId();
    }

    @Test
    void concurrentCallers_allGetTheSameFullyMergedAnswer() throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(CALLERS);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<ProductPricingResponse>> results = new ArrayList<>();
        try {
            for (int i = 0; i < CALLERS; i++) {
                results.add(callers.submit(() -> {
                    startGun.await(); // every thread waits here, then they all go at once
                    return productPricingService.pricing(productId).get(60, TimeUnit.SECONDS);
                }));
            }
            startGun.countDown();

            for (Future<ProductPricingResponse> result : results) {
                ProductPricingResponse pricing = result.get(60, TimeUnit.SECONDS);
                assertThat(pricing.getProduct().getId()).isEqualTo(productId);
                // Prices run 100.00 to 107.00, so the average is fixed no matter who wins the race.
                assertThat(pricing.getCategoryStats().getProductCount()).isEqualTo(PRODUCTS);
                assertThat(pricing.getCategoryStats().getAveragePrice()).isEqualByComparingTo("103.50");
                // Seven siblings exist, the limit keeps five, and the product itself is excluded.
                assertThat(pricing.getRelatedProducts()).hasSize(RELATED_LIMIT);
                assertThat(pricing.getRelatedProducts()).noneMatch(related -> related.getId().equals(productId));
                // The partner is unreachable, so 2kg at the 2.50 flat rate.
                assertThat(pricing.getShipping().getSource()).isEqualTo("FALLBACK");
                assertThat(pricing.getShipping().getAmount()).isEqualByComparingTo("5.00");
                assertThat(pricing.isDegraded()).isTrue();
            }
        } finally {
            callers.shutdownNow();
        }
    }

    /**
     * The unwrapping check: the caller must see the BusinessException, not a bare
     * CompletionException, otherwise the advice answers 500 instead of 404.
     */
    @Test
    void unknownProduct_failsTheFutureWithTheBusinessException() {
        CompletableFuture<ProductPricingResponse> pricing = productPricingService.pricing(-1L);

        assertThatThrownBy(pricing::join)
                .hasCauseInstanceOf(BusinessException.class)
                .hasMessageContaining("Product not found with id: -1");
    }

    /** One bad id must cost one error row, not the whole batch. */
    @Test
    void batch_reportsTheFailedIdAndKeepsTheGoodOnes() throws Exception {
        List<ProductPricingBatchItemResponse> items =
                productPricingService.pricingBatch(List.of(productId, -1L)).get(60, TimeUnit.SECONDS);

        assertThat(items).hasSize(2);
        assertThat(items.getFirst().getPricing()).isNotNull();
        assertThat(items.getFirst().getError()).isNull();
        assertThat(items.getLast().getPricing()).isNull();
        assertThat(items.getLast().getError()).contains("Product not found with id: -1");
    }
}
