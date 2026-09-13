package com.example.project2.dto.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#timeouts-and-fallback
 * A shipping price plus where it came from. The client needs to know whether it is a real quote,
 * because a FALLBACK price is not something you can charge without re-checking.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingQuoteResponse {

    private BigDecimal amount;
    private String currency;

    /** {@code PARTNER} when the remote call answered, {@code FALLBACK} when it failed or timed out. */
    private String source;
}
