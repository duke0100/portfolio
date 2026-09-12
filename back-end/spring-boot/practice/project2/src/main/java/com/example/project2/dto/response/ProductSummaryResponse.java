package com.example.project2.dto.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Interview topic: docs/interview/rest-api/05-pagination-and-sorting.md#paged-endpoint
 * The row shape of a catalog page. It leaves out the TEXT description column, which nobody reads
 * in a list but which would dominate the size of a 100-row page.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSummaryResponse {

    private Long id;
    private String name;
    private String brand;
    private BigDecimal price;
    private Integer stockQuantity;
}
