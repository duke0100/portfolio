package com.example.project2.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

/**
 * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#jointable-many-to-many
 * Tags arrive as plain names, and a name that does not exist yet is created on the way in.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddProductTagsRequest {

    @NotEmpty(message = "At least one tag name is required")
    private List<String> names;
}
