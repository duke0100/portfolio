package com.example.project2.exception;

import lombok.Getter;

/**
 * Interview topic: docs/interview/rest-api/03-global-exception-handling.md#domain-exceptions
 * (this class is the "not found" sample in that section)
 *
 * <p>Plain domain exception: it carries the id and no HTTP detail, the advice decides the status.
 */
@Getter
public class ProductNotFoundException extends RuntimeException {

    private final Long productId;

    public ProductNotFoundException(Long productId) {
        super("Product " + productId + " does not exist");
        this.productId = productId;
    }
}
