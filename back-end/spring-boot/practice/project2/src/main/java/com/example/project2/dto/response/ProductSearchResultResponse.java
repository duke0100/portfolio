package com.example.project2.dto.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#jpql
 * The row shape all three searches return. JPQL builds it directly with {@code select new ...}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResultResponse {

    private Long id;
    private String name;
    private String brand;
    private BigDecimal price;
    private String categoryName;

    /**
     * Match score from PostgreSQL's {@code ts_rank}. Always null for JPQL and Criteria, because
     * neither of them can produce it.
     */
    private Double searchRank;

    public ProductSearchResultResponse(Long id, String name, String brand, BigDecimal price, String categoryName) {
        this(id, name, brand, price, categoryName, null);
    }
}
