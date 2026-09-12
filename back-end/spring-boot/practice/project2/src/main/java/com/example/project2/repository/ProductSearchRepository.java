package com.example.project2.repository;

import com.example.project2.dto.request.ProductSearchRequest;
import com.example.project2.dto.response.ProductSearchResultResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#criteria-api
 * The Criteria API search. {@link ProductRepository} extends this, so callers use one repository
 * and never see an {@code EntityManager}.
 *
 * <p>Spring Data finds the code by name: the class must be called
 * {@code ProductSearchRepositoryImpl}.
 */
public interface ProductSearchRepository {

    Page<ProductSearchResultResponse> searchWithCriteria(ProductSearchRequest request, Pageable pageable);
}
