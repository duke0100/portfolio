package com.example.project2.dto.response;

import lombok.*;

import java.util.List;

/**
 * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#read-only-joincolumn
 * Flattens the self-referencing association so the JSON cannot recurse.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryTreeResponse {

    private Long id;
    private String name;

    /** Read from the writable parent_id column. */
    private Long parentId;

    /** Read through the read-only parent association, so no extra query is written by hand. */
    private String parentName;

    private List<String> childNames;
    private int childCount;
}
