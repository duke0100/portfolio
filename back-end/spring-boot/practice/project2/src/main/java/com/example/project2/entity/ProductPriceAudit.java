package com.example.project2.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#after-commit-listener
 * One row per committed price change. The row only exists if the price change really committed.
 *
 * <p>{@code workerThread} and {@code correlationId} are recorded so the async wiring is visible in
 * the data: an http thread name means {@code @Async} did not apply, an empty correlation id means
 * the {@code TaskDecorator} is gone.
 */
@Entity
@Table(name = "product_price_audits")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPriceAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The product id as a plain column, not a {@code @ManyToOne}. The listener runs after commit in
     * its own transaction and never needs the product graph, so a join buys nothing here.
     */
    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "old_price", precision = 15, scale = 2, nullable = false, updatable = false)
    private BigDecimal oldPrice;

    @Column(name = "new_price", precision = 15, scale = 2, nullable = false, updatable = false)
    private BigDecimal newPrice;

    @Column(name = "changed_by", length = 100, nullable = false, updatable = false)
    private String changedBy;

    /** Taken from MDC on the pool thread, which only works because of the TaskDecorator. */
    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    /** Name of the thread that wrote the row, so you can tell async from synchronous at a glance. */
    @Column(name = "worker_thread", length = 100, nullable = false, updatable = false)
    private String workerThread;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;
}
