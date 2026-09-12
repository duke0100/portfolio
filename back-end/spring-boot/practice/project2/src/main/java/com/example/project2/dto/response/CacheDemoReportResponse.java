package com.example.project2.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Interview topic: docs/interview/spring-data-jpa/01-first-level-vs-second-level-cache.md#measuring-it
 * Response body for the cache demo endpoint - the three statement counts are what show L1 and L2
 * actually doing their job, not just the annotations being present.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheDemoReportResponse {

    private Long categoryId;

    /** SQL fired loading the category for the very first time (L1 miss, L2 miss). */
    private long sqlStatementsFirstLoad;

    /** SQL fired asking for the same id again in the same persistence context (L1 hit). */
    private long sqlStatementsL1Repeat;

    /** SQL fired loading the same id again in a brand-new transaction (served from L2 instead). */
    private long sqlStatementsAfterClearL2Hit;

    /** True: the repeat call within the same transaction returned the exact same Java object. */
    private boolean sameInstanceWithinSession;

    /** False: a new transaction builds a new instance from the cached state, not the same object. */
    private boolean sameInstanceAfterClear;

    private long secondLevelCacheHitCount;

    private long secondLevelCacheMissCount;
}
