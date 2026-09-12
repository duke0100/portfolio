package com.example.project2.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

/**
 * Interview topic: docs/interview/rest-api/02-request-body-vs-model-attribute.md#requestbody-json
 * The JSON side of the demo: this object is filled by Jackson from the raw request body.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductNoteRequest {

    @NotNull(message = "Product id is required")
    private Long productId;

    @NotBlank(message = "Author is required")
    private String author;

    @NotBlank(message = "Message is required")
    private String message;

    @Min(1)
    @Max(5)
    private Integer rating;

    /** A real list in JSON, which is exactly the shape form data cannot express cleanly. */
    private List<String> tags;
}
