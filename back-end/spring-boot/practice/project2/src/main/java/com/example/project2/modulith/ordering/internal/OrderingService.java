package com.example.project2.modulith.ordering.internal;

import com.example.project2.modulith.catalogue.external.CatalogueFacade;
import com.example.project2.modulith.catalogue.external.CatalogueItem;
import com.example.project2.modulith.core.OrderPlaced;
import com.example.project2.modulith.core.TenantHolder;
import com.example.project2.modulith.ordering.external.OrderReceipt;
import com.example.project2.modulith.ordering.external.OrderingFacade;
import com.example.project2.modulith.ordering.external.PlaceOrderCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#facade-sync
 * (this class is the publisher side of both the facade and the event samples)
 *
 * <p>Shows the two ways modules talk: a direct facade call into catalogue, then an event for
 * loyalty. The method must be transactional, otherwise there is no commit for the outbox to hang
 * the event on.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class OrderingService implements OrderingFacade {

    private final CatalogueFacade catalogueFacade;
    private final ApplicationEventPublisher events;

    /**
     * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#event-driven-async
     * (this method is the publish-inside-a-transaction sample there)
     */
    @Override
    @Transactional
    public OrderReceipt placeOrder(PlaceOrderCommand command) {
        // Sync, in-JVM call across the module boundary - a missing SKU fails the whole order.
        CatalogueItem item = catalogueFacade.requireItem(command.sku());

        BigDecimal total = item.unitPrice().multiply(BigDecimal.valueOf(command.quantity()));
        String orderId = UUID.randomUUID().toString();
        log.info("Order {} placed for customer {}", orderId, command.customerId());

        // Async, one-way. Loyalty is never named here, so ordering does not depend on it.
        events.publishEvent(new OrderPlaced(orderId, command.customerId(), command.sku(),
                command.quantity(), total, TenantHolder.get(), Instant.now()));

        return new OrderReceipt(orderId, command.customerId(), command.sku(), item.name(),
                command.quantity(), total);
    }
}
