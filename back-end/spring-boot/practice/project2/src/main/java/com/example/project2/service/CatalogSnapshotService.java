package com.example.project2.service;

import com.example.project2.dto.response.CatalogSnapshotResponse;

/**
 * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#callable-fan-out
 * Builds one dashboard view out of three independent catalog queries.
 */
public interface CatalogSnapshotService {

    /**
     * Runs the three queries at the same time and merges them.
     *
     * @throws com.example.commonlib.exception.BusinessException 404 when the category does not exist
     */
    CatalogSnapshotResponse snapshot(Long categoryId);
}
