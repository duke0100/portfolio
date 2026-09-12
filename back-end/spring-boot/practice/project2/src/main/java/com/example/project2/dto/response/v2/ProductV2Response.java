package com.example.project2.dto.response.v2;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Interview topic: docs/interview/rest-api/01-rest-api-versioning.md#the-breaking-change
 *
 * <p>v2 of the product payload, deliberately breaking: the flat price, category and dimensions
 * fields all move into nested objects. A v1 client cannot read this, which is exactly why both
 * versions have to stay available.
 */
@Getter
@Builder
public class ProductV2Response {

    private Long id;
    private String name;
    private String sku;
    private String status;
    private Integer stockQuantity;
    private CategoryRef category;
    private Pricing pricing;
    private Dimensions dimensions;
    private LocalDateTime updatedAt;

    /** Replaces the flat {@code categoryId} + {@code categoryName} pair of v1. */
    public record CategoryRef(Long id, String name) {
    }

    /** Replaces v1's flat price fields, and adds the discounted price v1 clients had to work out. */
    public record Pricing(BigDecimal listPrice,
                          BigDecimal discountPercent,
                          BigDecimal effectivePrice,
                          String currency) {
    }

    /** Replaces the free-text {@code dimensions} string of v1. */
    public record Dimensions(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {
    }
}
