package com.example.project2.service;

import com.example.project2.dto.request.ProductSearchRequest;
import com.example.project2.dto.response.ProductSearchReportResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#running-all-three
 * One product search, three query styles - one enum value each.
 */
public interface ProductSearchService {

    enum QueryStyle {
        /** Fixed JPQL text with {@code :param is null or ...} parts. Runs on any database. */
        JPQL,
        /** Built in Java code from the filters that actually arrived. */
        CRITERIA,
        /** PostgreSQL full-text search with {@code ts_rank}. Runs only there. */
        NATIVE
    }

    ProductSearchReportResponse search(QueryStyle style, ProductSearchRequest request, Pageable pageable);

    /** Runs every style on the same filters. NATIVE runs only if {@code text} was sent. */
    List<ProductSearchReportResponse> compare(ProductSearchRequest request, Pageable pageable);
}
