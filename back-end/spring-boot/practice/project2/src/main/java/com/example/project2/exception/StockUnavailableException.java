package com.example.project2.exception;

import lombok.Getter;

/**
 * Interview topic: docs/interview/rest-api/03-global-exception-handling.md#domain-exceptions
 * (this class is the "conflict" sample in that section)
 *
 * <p>The three numbers below become extra members of the RFC 7807 body, so the client can retry
 * with a quantity that fits instead of guessing from the message text.
 */
@Getter
public class StockUnavailableException extends RuntimeException {

    private final Long productId;
    private final int requested;
    private final int available;

    public StockUnavailableException(Long productId, int requested, int available) {
        super("Only " + available + " item(s) left for product " + productId);
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }
}
