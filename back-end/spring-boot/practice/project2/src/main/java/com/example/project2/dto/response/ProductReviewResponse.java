package com.example.project2.dto.response;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#reading-the-collection
 * Carries the product id instead of the product, because returning the entity would serialise in a loop.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewResponse {

    private Long id;
    private Long productId;
    private String productName;
    private String authorName;
    private Integer rating;

    /** The enum name, which is exactly what sits in the column. */
    private String status;

    private String title;
    private String comment;
    private LocalDateTime createdAt;
}
