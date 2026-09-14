package com.example.project2.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project2.dto.request.ChangeProductPriceRequest;
import com.example.project2.dto.response.AsyncDispatchProbeResponse;
import com.example.project2.dto.response.ProductPriceAuditResponse;
import com.example.project2.entity.Category;
import com.example.project2.entity.Product;
import com.example.project2.repository.CategoryRepository;
import com.example.project2.repository.ProductPriceAuditRepository;
import com.example.project2.repository.ProductRepository;
import com.example.project2.service.AsyncDispatchProbeService;
import com.example.project2.service.ProductPriceAuditService;
import com.example.project2.service.ProductPriceChangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#under-load
 * (this is the concurrency test quoted in that section)
 *
 * <p>Three invariants: 16 editors racing on one price produce exactly one winner and exactly one
 * audit row, a rolled-back price change produces none, and the self-invoked call never leaves the
 * caller's thread.
 *
 * <p>No test here sleeps. The audit row is waited for with Awaitility, which polls the invariant
 * and stops the moment it holds.
 *
 * <p>Runs on H2. The compare-and-set UPDATE behaves the same on Postgres, but H2 commits far
 * faster, so this asserts correctness and never timing.
 */
@SpringBootTest(properties = {
        "catalog.async.pool-size=2",
        "catalog.async.queue-capacity=64",
        "catalog.async.probe-timeout=PT10S"
})
class AsyncPriceAuditConcurrencyTest {

    private static final int EDITORS = 16;
    private static final BigDecimal START_PRICE = new BigDecimal("100.00");
    private static final Duration AUDIT_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private ProductPriceChangeService priceChangeService;

    @Autowired
    private ProductPriceAuditService auditService;

    @Autowired
    private AsyncDispatchProbeService dispatchProbeService;

    @Autowired
    private ProductPriceAuditRepository auditRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long productId;
    private Long otherProductId;

    @BeforeEach
    void seedCatalog() {
        Category category = categoryRepository.save(Category.builder()
                .name("Price audit category")
                .build());
        productId = newProduct(category, "primary").getId();
        otherProductId = newProduct(category, "marker").getId();
    }

    /**
     * The race: every editor read 100.00 and every one of them tries to move it. Only the UPDATE's
     * WHERE clause can decide the winner, so exactly one succeeds and the rest get a 409.
     */
    @Test
    void sixteenEditorsRacingOnOnePrice_produceOneWinnerAndOneAuditRow() throws Exception {
        ExecutorService editors = Executors.newFixedThreadPool(EDITORS);
        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger conflicts = new AtomicInteger();
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < EDITORS; i++) {
                int editor = i;
                results.add(editors.submit(() -> {
                    startGun.await(); // everyone waits here, then they all go at once
                    try {
                        priceChangeService.changePrice(productId, new ChangeProductPriceRequest(
                                START_PRICE, START_PRICE.add(BigDecimal.valueOf(editor + 1L)), "editor-" + editor));
                        return true;
                    } catch (BusinessException e) {
                        conflicts.incrementAndGet();
                        return false;
                    }
                }));
            }
            startGun.countDown();

            int winners = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
            assertThat(conflicts).hasValue(EDITORS - 1);
        } finally {
            editors.shutdownNow();
        }

        // The row lands a moment after the response, which is exactly what @Async bought us.
        await().atMost(AUDIT_TIMEOUT).until(() -> auditRepository.countByProductId(productId) == 1);

        ProductPriceAuditResponse audit = auditService.recentChanges(productId).getFirst();
        assertThat(audit.oldPrice()).isEqualByComparingTo(START_PRICE);
        // The 15 losers never committed, so none of them produced a row.
        assertThat(auditRepository.countByProductId(productId)).isEqualTo(1);
        // Proof the listener really ran on the pool and not on the caller's thread.
        assertThat(audit.workerThread()).startsWith("catalog-async-");
    }

    /**
     * AFTER_COMMIT is the whole point: a price change that rolls back must leave no audit trail.
     *
     * <p>There is no sleep and no "wait for nothing to happen" here. A second product is changed
     * successfully afterwards, and once its row has landed the listener has clearly had its turn.
     */
    @Test
    void rolledBackPriceChange_writesNoAuditRow() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            priceChangeService.changePrice(productId, new ChangeProductPriceRequest(
                    START_PRICE, new BigDecimal("123.45"), "editor-rollback"));
            throw new IllegalStateException("payment declined after the price was written");
        })).isInstanceOf(IllegalStateException.class);

        priceChangeService.changePrice(otherProductId, new ChangeProductPriceRequest(
                START_PRICE, new BigDecimal("111.00"), "editor-marker"));
        await().atMost(AUDIT_TIMEOUT).until(() -> auditRepository.countByProductId(otherProductId) == 1);

        assertThat(auditRepository.countByProductId(productId)).isZero();
        assertThat(productRepository.findById(productId).orElseThrow().getPrice())
                .isEqualByComparingTo(START_PRICE);
    }

    /** The pitfall itself, asserted: a self-call bypasses the proxy and stays on the caller's thread. */
    @Test
    void selfInvocationStaysOnTheCallerThread_whileTheProxiedCallGoesToThePool() {
        AsyncDispatchProbeResponse probe = dispatchProbeService.probe();

        assertThat(probe.selfInvocationThread()).isEqualTo(probe.callerThread());
        assertThat(probe.selfInvocationRanAsync()).isFalse();
        assertThat(probe.proxiedThread()).startsWith("catalog-async-");
        assertThat(probe.proxiedRanAsync()).isTrue();
        // Not void and not a Future, so Spring 6 refuses the call instead of running it.
        assertThat(probe.wrongReturnTypeOutcome())
                .startsWith("rejected at call time")
                .contains("only Future and void supported");
    }

    private Product newProduct(Category category, String suffix) {
        return productRepository.save(Product.builder()
                .category(category)
                .name("Price audit probe " + suffix)
                .sku("AUDIT-" + System.nanoTime() + "-" + suffix)
                .price(START_PRICE)
                .weight(new BigDecimal("1.000"))
                .stockQuantity(25)
                .lowStockThreshold(5)
                .build());
    }
}
