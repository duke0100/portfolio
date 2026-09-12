package com.example.project2.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#measuring-it
 * Response body for the N+1 demo endpoint - {@code sqlStatements} is the number that shows
 * whether a fix actually worked.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NPlusOneReportResponse {

    private String strategy;
    private String note;

    /** Rows returned in this page. */
    private int rows;

    /** JDBC statements Hibernate prepared while producing those rows. */
    private long sqlStatements;

    /** Round trips Hibernate made to load collections, not the number of collections loaded. */
    private long collectionsFetched;

    /** False if statistics were never turned on - then the counts above are just zero, not real data. */
    private boolean statisticsEnabled;

    private long totalElements;

    private List<OrderSummaryResponse> orders;
}
