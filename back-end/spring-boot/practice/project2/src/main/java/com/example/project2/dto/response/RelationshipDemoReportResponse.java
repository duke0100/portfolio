package com.example.project2.dto.response;

import lombok.*;

/**
 * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#seeing-it-fail
 * The outcome and the statement count together show which side actually wrote the FK.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationshipDemoReportResponse {

    private String scenario;

    /** What the scenario did, in one line. */
    private String note;

    /** OK when the flush succeeded, FAILED when the database rejected it. */
    private String outcome;

    /** Only filled in when the flush failed. */
    private String error;

    /** How many reviews the in-memory product ended up holding. */
    private int reviewsInMemory;

    /** Rows counted in product_reviews after the flush, or -1 if the flush failed. */
    private long reviewsInDatabase;

    /** JDBC statements Hibernate prepared while the scenario ran. */
    private long sqlStatements;

    /** Always true, because the demo rolls back so it leaves no rows behind. */
    private boolean rolledBack;
}
