package com.example.project2.service.impl;

import com.example.project2.dto.request.ProductSearchRequest;
import com.example.project2.dto.response.ProductSearchReportResponse;
import com.example.project2.dto.response.ProductSearchResultResponse;
import com.example.project2.repository.ProductRepository;
import com.example.project2.repository.projection.ProductSearchProjection;
import com.example.project2.service.ProductSearchService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#running-all-three
 * Sends one {@link ProductSearchRequest} to the JPQL, Criteria and native searches, and reports how
 * many SQL statements each one used.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchServiceImpl implements ProductSearchService {

    private final ProductRepository productRepository;
    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;

    @Override
    @Transactional(readOnly = true)
    public ProductSearchReportResponse search(QueryStyle style, ProductSearchRequest request, Pageable pageable) {
        log.debug("Product search via {} style", style);
        return measure(style, request, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSearchReportResponse> compare(ProductSearchRequest request, Pageable pageable) {
        List<ProductSearchReportResponse> reports = new ArrayList<>();
        reports.add(measure(QueryStyle.JPQL, request, pageable));
        reports.add(measure(QueryStyle.CRITERIA, request, pageable));
        if (StringUtils.hasText(request.getText())) {
            reports.add(measure(QueryStyle.NATIVE, request, pageable));
        }
        return reports;
    }

    private ProductSearchReportResponse measure(QueryStyle style, ProductSearchRequest request, Pageable pageable) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        entityManager.clear();
        long statementsBefore = statistics.getPrepareStatementCount();

        Page<ProductSearchResultResponse> page = switch (style) {
            case JPQL -> jpql(request, pageable);
            case CRITERIA -> productRepository.searchWithCriteria(request, pageable);
            case NATIVE -> nativeFullText(request, pageable);
        };

        return ProductSearchReportResponse.builder()
                .style(style.name())
                .note(noteFor(style))
                .skipsUnusedFilters(style == QueryStyle.CRITERIA)
                .rows(page.getNumberOfElements())
                .totalElements(page.getTotalElements())
                .sqlStatements(statistics.getPrepareStatementCount() - statementsBefore)
                .products(page.getContent())
                .build();
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#jpql
     * Every filter is sent on every call. A null value just makes its {@code is null} part true.
     */
    private Page<ProductSearchResultResponse> jpql(ProductSearchRequest request, Pageable pageable) {
        return productRepository.searchWithJpql(
                emptyToNull(request.getName()),
                emptyToNull(request.getBrand()),
                emptyToNull(request.getStatus()),
                request.getCategoryId(),
                request.getMinPrice(),
                request.getMaxPrice(),
                Boolean.TRUE.equals(request.getInStock()),
                pageable);
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#native-query
     * The sort is dropped on purpose: Spring Data would add its own {@code order by} and break the
     * query's {@code order by search_rank desc}.
     */
    private Page<ProductSearchResultResponse> nativeFullText(ProductSearchRequest request, Pageable pageable) {
        if (!StringUtils.hasText(request.getText())) {
            throw new IllegalArgumentException("The NATIVE style needs a 'text' value to feed plainto_tsquery");
        }
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return productRepository.searchWithFullText(
                        request.getText(), emptyToNull(request.getBrand()), request.getMinPrice(), unsorted)
                .map(this::toResult);
    }

    private ProductSearchResultResponse toResult(ProductSearchProjection projection) {
        return ProductSearchResultResponse.builder()
                .id(projection.getId())
                .name(projection.getName())
                .brand(projection.getBrand())
                .price(projection.getPrice())
                .categoryName(projection.getCategoryName())
                .searchRank(projection.getSearchRank())
                .build();
    }

    private String noteFor(QueryStyle style) {
        return switch (style) {
            case JPQL -> "Written over entity classes, runs on any database; the filter list is fixed";
            case CRITERIA -> "Type-safe and dynamic; only the filters you send reach the SQL";
            case NATIVE -> "PostgreSQL full-text search, sorted by ts_rank";
        };
    }

    /** An empty parameter must behave like a missing one, or the JPQL null check never matches. */
    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
