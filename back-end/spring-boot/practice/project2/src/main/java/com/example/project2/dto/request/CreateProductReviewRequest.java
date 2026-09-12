package com.example.project2.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#syncing-both-sides
 * The product comes from the URL, so a client cannot review a different product than the path says.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductReviewRequest {

    @NotBlank(message = "Author name is required")
    @Size(max = 150, message = "Author name must be at most 150 characters")
    private String authorName;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer rating;

    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    private String comment;
}
