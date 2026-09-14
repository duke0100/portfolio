package com.example.project2.listener;

import com.example.project2.event.ProductPriceChanged;
import com.example.project2.service.ProductPriceAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#after-commit-listener
 * (this class is the @Async + @TransactionalEventListener code sample in that section)
 *
 * <p>A plain {@code @EventListener} would run while the price change is still uncommitted, so the
 * audit row could describe a change that then rolled back. AFTER_COMMIT means no commit, no row.
 *
 * <p>{@code @Async} is what keeps the caller's response fast - without it this write happens on the
 * request thread after commit, and the client waits for it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductPriceAuditListener {

    private final ProductPriceAuditService auditService;

    /**
     * Returns void, so a failure here cannot reach the caller. It goes to the
     * AsyncUncaughtExceptionHandler in CatalogAsyncConfig instead of disappearing.
     */
    @Async("catalogAsyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPriceChanged(ProductPriceChanged event) {
        auditService.record(event);
    }
}
