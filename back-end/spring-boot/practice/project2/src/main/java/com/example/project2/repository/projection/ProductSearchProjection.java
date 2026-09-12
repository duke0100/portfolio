package com.example.project2.repository.projection;

import java.math.BigDecimal;

/**
 * Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#native-query
 * What the native search returns. A native query can only give back a real entity if it selects
 * every mapped column, and here we select fewer on purpose.
 *
 * <p>Getter names must match the SQL aliases. Spring Data also reads snake_case, so
 * {@code as category_name} fills {@link #getCategoryName()}.
 */
public interface ProductSearchProjection {

    Long getId();

    String getName();

    String getBrand();

    BigDecimal getPrice();

    String getCategoryName();

    Double getSearchRank();
}
