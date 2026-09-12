package com.example.project2.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project2.dto.response.ProductScrollResponse;
import com.example.project2.dto.response.ProductSummaryResponse;
import com.example.project2.repository.ProductRepository;
import com.example.project2.service.ProductCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Interview topic: docs/interview/rest-api/05-pagination-and-sorting.md#guarding-the-sort
 * The sort and the page number arrive straight from the URL, so both are checked here before they
 * reach the database. Without these checks one request can sort or skip millions of rows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCatalogServiceImpl implements ProductCatalogService {

    /**
     * Sort properties a client may ask for. Each one has an index behind it, added by
     * V11__add_product_sort_indexes.sql. Anything else is a 400.
     */
    private static final List<String> SORTABLE = List.of("createdAt", "id", "name", "price");

    /** Rows a caller may skip with page/size. Past this the page is refused, not served slowly. */
    private static final long MAX_OFFSET = 10_000L;

    /** Batch cap for the scroll endpoint. It mirrors spring.data.web.pageable.max-page-size. */
    private static final int MAX_SCROLL_SIZE = 100;

    private final ProductRepository productRepository;

    /**
     * Interview topic: docs/interview/rest-api/05-pagination-and-sorting.md#paged-endpoint
     * Read-only transaction because open-session-in-view is off in this module, so the rows have to
     * be read while a transaction is still running.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> findPage(Pageable pageable) {
        rejectUnknownSort(pageable.getSort());
        rejectDeepOffset(pageable);
        log.debug("Catalog page {} size {} sort {}",
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());
        return productRepository.findSummaries(pageable);
    }

    /**
     * Interview topic: docs/interview/rest-api/05-pagination-and-sorting.md#slice-and-cursor-based-pagination
     * The batch size is clamped rather than rejected, which is how Spring itself treats a size
     * parameter above max-page-size.
     */
    @Override
    @Transactional(readOnly = true)
    public ProductScrollResponse scroll(Long afterId, int size) {
        int batch = Math.clamp(size, 1, MAX_SCROLL_SIZE);
        Slice<ProductSummaryResponse> slice =
                productRepository.findSummariesAfterId(afterId, Pageable.ofSize(batch));
        List<ProductSummaryResponse> content = slice.getContent();
        Long nextAfterId = slice.hasNext() && !content.isEmpty()
                ? content.get(content.size() - 1).getId()
                : null;
        return new ProductScrollResponse(content, slice.hasNext(), nextAfterId);
    }

    /**
     * An unknown property makes Spring Data throw {@code PropertyReferenceException}, which the
     * client sees as a 500. A known but unindexed one is a full sort of the table, so the same
     * allow-list decides both the error and the cost.
     */
    private void rejectUnknownSort(Sort sort) {
        List<String> unknown = sort.stream()
                .map(Sort.Order::getProperty)
                .filter(property -> !SORTABLE.contains(property))
                .toList();
        if (!unknown.isEmpty()) {
            throw BusinessException.badRequest(
                    "Cannot sort products by " + unknown + ". Sortable properties: " + SORTABLE);
        }
    }

    /**
     * Page 50000 makes PostgreSQL read and throw away a million rows before it returns twenty.
     * Past the cap the request is refused, and the message tells the caller to narrow it down.
     */
    private void rejectDeepOffset(Pageable pageable) {
        if (pageable.getOffset() > MAX_OFFSET) {
            throw BusinessException.badRequest(
                    "Page %d skips %d rows, more than the %d allowed. Narrow the request with filters or a smaller page window."
                            .formatted(pageable.getPageNumber(), pageable.getOffset(), MAX_OFFSET));
        }
    }
}
