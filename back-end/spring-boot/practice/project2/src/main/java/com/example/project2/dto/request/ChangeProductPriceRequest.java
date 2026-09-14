package com.example.project2.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#the-price-change
 * A price change, sent with the price the caller believes is current.
 *
 * <p>{@code expectedPrice} is what makes two concurrent editors safe: the UPDATE only matches while
 * that price is still in the row, so the second editor gets a 409 instead of overwriting the first.
 */
public record ChangeProductPriceRequest(

        @NotNull(message = "expectedPrice is required")
        @DecimalMin(value = "0.00", message = "expectedPrice cannot be negative")
        @DecimalMax(value = "9999999999999.99", message = "expectedPrice is out of range")
        BigDecimal expectedPrice,

        @NotNull(message = "newPrice is required")
        @DecimalMin(value = "0.01", message = "newPrice must be greater than zero")
        @DecimalMax(value = "9999999999999.99", message = "newPrice is out of range")
        BigDecimal newPrice,

        @NotBlank(message = "changedBy is required")
        @Size(max = 100, message = "changedBy must be at most 100 characters")
        String changedBy) {
}
