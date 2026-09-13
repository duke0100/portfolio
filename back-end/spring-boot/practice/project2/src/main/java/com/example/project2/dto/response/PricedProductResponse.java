package com.example.project2.dto.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#the-pipeline
 * The first stage's result. It carries {@code categoryId} because the next stage needs it, which
 * is what makes the chain a {@code thenCompose} and not three independent calls.
 *
 * <p>The field order is the argument order of the JPQL constructor expression in
 * {@code ProductRepository.findPricingProjection}. Reordering fields breaks that query.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricedProductResponse {

    private Long id;
    private String name;
    private String brand;
    private BigDecimal price;

    /** Kilograms. Null for products nobody has weighed, which the shipping stage treats as 1kg. */
    private BigDecimal weight;

    /** Null for an uncategorised product, and then the category stages are skipped. */
    private Long categoryId;
}
