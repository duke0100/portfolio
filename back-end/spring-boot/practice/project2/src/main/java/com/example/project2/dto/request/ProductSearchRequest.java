package com.example.project2.dto.request;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;

/**
 * Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#criteria-api
 * One filter object for all three searches, so they always get the same input. Every field is
 * optional, and that is the main reason the Criteria API exists.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchRequest {

    /** "Contains" match on {@code Product.name}, upper and lower case treated the same. */
    private String name;

    private String brand;

    private Long categoryId;

    private String status;

    @PositiveOrZero
    private BigDecimal minPrice;

    @PositiveOrZero
    private BigDecimal maxPrice;

    /** {@code true} keeps only rows that still have stock; {@code null} means "any". */
    private Boolean inStock;

    /**
     * Free text, used by the native search only. It goes to PostgreSQL's
     * {@code plainto_tsquery}, which JPQL and Criteria cannot do.
     */
    private String text;
}
