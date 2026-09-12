package com.example.project2.modulith.core;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#event-driven-async
 * (this record is the shared event sample in that section)
 *
 * <p>Shared event record: ordering publishes it, loyalty consumes it. It carries plain values only,
 * because the outbox serialises it to JSON and a listener may read it minutes after the publisher
 * committed.
 */
public record OrderPlaced(String orderId,
                          String customerId,
                          String sku,
                          int quantity,
                          BigDecimal total,
                          String tenant,
                          Instant placedAt) {
}
