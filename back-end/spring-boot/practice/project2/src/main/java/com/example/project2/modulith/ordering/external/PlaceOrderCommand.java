package com.example.project2.modulith.ordering.external;

/** Input of the ordering facade - a module-owned record, not a web DTO. */
public record PlaceOrderCommand(String customerId, String sku, int quantity) {
}
