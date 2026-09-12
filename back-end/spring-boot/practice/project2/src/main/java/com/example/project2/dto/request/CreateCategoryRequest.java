package com.example.project2.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCategoryRequest {

    @NotBlank(message = "Category name is required")
    private String name;

    private String description;

    private Long parentId;

    private String imageUrl;

    @NotBlank(message = "Slug is required")
    private String slug;

    private Integer displayOrder;

    private Boolean isFeatured;

    private String metaTitle;

    private String metaDescription;
}
