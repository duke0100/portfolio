package com.example.project2.dto.response;

import lombok.*;

import java.util.List;

/**
 * Interview topic: docs/interview/rest-api/02-request-body-vs-model-attribute.md#answer
 * One response shape for both endpoints, so the only visible difference is how the input arrived.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductNoteResponse {

    /** Either "@RequestBody" or "@ModelAttribute" - says which annotation filled the object. */
    private String boundBy;

    /** The content type the endpoint accepts, so the two rows are easy to compare. */
    private String readFrom;

    private Long productId;

    private String author;

    private String message;

    private Integer rating;

    private List<String> tags;

    /** Name of the product the note points at, or null when the id does not exist. */
    private String productName;
}
