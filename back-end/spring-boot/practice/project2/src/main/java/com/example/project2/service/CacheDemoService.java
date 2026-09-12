package com.example.project2.service;

import com.example.project2.dto.response.CacheDemoReportResponse;

/**
 * Interview topic: docs/interview/spring-data-jpa/01-first-level-vs-second-level-cache.md#measuring-it
 * (the L1-vs-L2 demo run behind {@code /api/v1/performance/cache-demo})
 */
public interface CacheDemoService {

    CacheDemoReportResponse run(Long categoryId);
}
