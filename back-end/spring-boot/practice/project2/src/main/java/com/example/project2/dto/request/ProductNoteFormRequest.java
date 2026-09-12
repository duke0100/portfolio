package com.example.project2.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Interview topic: docs/interview/rest-api/02-request-body-vs-model-attribute.md#modelattribute-form-and-query
 * The form/query side of the demo: Spring creates this object and calls a setter per parameter,
 * so it needs a no-argument constructor and setters, unlike the JSON request above.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductNoteFormRequest {

    @NotNull(message = "Product id is required")
    private Long productId;

    @NotBlank(message = "Author is required")
    private String author;

    @NotBlank(message = "Message is required")
    private String message;

    /** Arrives as the text "4" and is converted to a number by Spring, not by Jackson. */
    @Min(1)
    @Max(5)
    private Integer rating;

    /** Flat text only: "new,sale" has to be split by hand because form data has no list type. */
    private String tags;
}
