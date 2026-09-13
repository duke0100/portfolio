package com.example.project2.dto.response;

import lombok.*;

/**
 * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#fan-in-with-allof
 * One row of a batch response. A failed id carries an {@code error} instead of a pricing block,
 * so one bad id does not lose the nineteen good ones.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPricingBatchItemResponse {

    private Long productId;

    /** Null when this id failed. */
    private ProductPricingResponse pricing;

    /** Null when this id succeeded. */
    private String error;
}
