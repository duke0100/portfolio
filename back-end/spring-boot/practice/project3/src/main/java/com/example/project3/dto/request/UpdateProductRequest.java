package com.example.project3.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {

    private UUID categoryId;

    private String categoryName;

    private String name;

    private String description;

    private BigDecimal price;

    private Integer stockQuantity;

    private String sku;

    private String imageUrl;

    private BigDecimal weight;

    private String status;

    private String brand;

    private String dimensions;

    private BigDecimal discountPercent;
}
