package com.example.project2.dto.response;

import lombok.*;

import java.util.List;

/**
 * Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#running-all-three
 * One report per query style, so the rows and the SQL counts can be compared side by side.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchReportResponse {

    /** JPQL, CRITERIA or NATIVE. */
    private String style;

    /** One short line about that style. */
    private String note;

    /** {@code true} when the style leaves the empty filters out of the SQL. */
    private boolean skipsUnusedFilters;

    private int rows;

    private long totalElements;

    /** Counted by Hibernate, so one extra select is easy to spot. */
    private long sqlStatements;

    private List<ProductSearchResultResponse> products;
}
