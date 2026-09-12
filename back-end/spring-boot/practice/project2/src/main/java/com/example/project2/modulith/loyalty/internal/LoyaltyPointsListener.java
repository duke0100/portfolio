package com.example.project2.modulith.loyalty.internal;

import com.example.project2.modulith.core.OrderPlaced;
import com.example.project2.modulith.core.SkuNameLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#event-driven-async
 * (this class is the @ApplicationModuleListener code sample in that section)
 *
 * <p>{@code @ApplicationModuleListener} is async + after-commit + its own transaction, and Modulith
 * writes a row in {@code event_publication} before calling it. If this method throws, the row stays
 * incomplete and can be replayed instead of the points being silently lost.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LoyaltyPointsListener {

    private final SkuNameLookup skuNameLookup;
    private final LoyaltyLedger ledger;

    @ApplicationModuleListener
    void on(OrderPlaced event) {
        // Cross-module read through a core-common contract, not through the catalogue module.
        String productName = skuNameLookup.findNameBySku(event.sku()).orElse(event.sku());

        int earned = event.total().intValue();
        ledger.award(event.customerId(), earned, "%d points for %s (order %s, tenant %s)"
                .formatted(earned, productName, event.orderId(), event.tenant()));
        log.info("Awarded {} loyalty points to {}", earned, event.customerId());
    }
}
