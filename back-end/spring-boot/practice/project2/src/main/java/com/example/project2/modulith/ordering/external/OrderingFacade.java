package com.example.project2.modulith.ordering.external;

/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#facade-sync
 * (this interface is the second facade sample in that section)
 *
 * <p>The main module (the aggregator) calls this; nothing else in the slice does.
 */
public interface OrderingFacade {

    OrderReceipt placeOrder(PlaceOrderCommand command);
}
