package com.example.project2.service;

import com.example.project2.dto.response.ProductScrollResponse;
import com.example.project2.dto.response.ProductSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Interview topic: docs/interview/rest-api/05-pagination-and-sorting.md#paged-endpoint
 * The two ways this catalog hands out rows: numbered pages for a table with a pager, and a cursor
 * scroll for an endless list.
 */
public interface ProductCatalogService {

    /** One numbered page, sorted by whatever the caller asked for, within the allow-list. */
    Page<ProductSummaryResponse> findPage(Pageable pageable);

    /** The next batch after {@code afterId}. Pass null for the first batch. */
    ProductScrollResponse scroll(Long afterId, int size);
}
