package com.example.project2.repository.impl;

import com.example.project2.dto.request.ProductSearchRequest;
import com.example.project2.dto.response.ProductSearchResultResponse;
import com.example.project2.entity.Category;
import com.example.project2.entity.Category_;
import com.example.project2.entity.Product;
import com.example.project2.entity.Product_;
import com.example.project2.repository.ProductSearchRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#criteria-api
 * Builds the search in Java code, so only the filters the caller really sent become conditions.
 * {@code Product_} and {@code Category_} are generated during the build, which is what lets the
 * compiler check the field names.
 */
@RequiredArgsConstructor
public class ProductSearchRepositoryImpl implements ProductSearchRepository {

    private final EntityManager entityManager;

    @Override
    public Page<ProductSearchResultResponse> searchWithCriteria(ProductSearchRequest request, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<ProductSearchResultResponse> query = cb.createQuery(ProductSearchResultResponse.class);
        Root<Product> product = query.from(Product.class);
        Join<Product, Category> category = product.join(Product_.category, JoinType.LEFT);

        query.select(cb.construct(ProductSearchResultResponse.class,
                        product.get(Product_.id),
                        product.get(Product_.name),
                        product.get(Product_.brand),
                        product.get(Product_.price),
                        category.get(Category_.name)))
                .where(toPredicates(cb, product, request))
                .orderBy(cb.asc(product.get(Product_.id)));

        List<ProductSearchResultResponse> rows = entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        // No count query is needed when the first page already tells us the total.
        return PageableExecutionUtils.getPage(rows, pageable, () -> count(cb, request));
    }

    /** Same conditions, but a new root - one root cannot be shared by two queries. */
    private long count(CriteriaBuilder cb, ProductSearchRequest request) {
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Product> product = countQuery.from(Product.class);
        countQuery.select(cb.count(product)).where(toPredicates(cb, product, request));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#criteria-api
     * The dynamic part: an empty field simply adds no condition.
     */
    private Predicate[] toPredicates(CriteriaBuilder cb, Root<Product> product, ProductSearchRequest request) {
        List<Predicate> predicates = new ArrayList<>();

        if (StringUtils.hasText(request.getName())) {
            predicates.add(cb.like(cb.lower(product.get(Product_.name)),
                    "%" + request.getName().toLowerCase() + "%"));
        }
        if (StringUtils.hasText(request.getBrand())) {
            predicates.add(cb.equal(product.get(Product_.brand), request.getBrand()));
        }
        if (StringUtils.hasText(request.getStatus())) {
            predicates.add(cb.equal(product.get(Product_.status), request.getStatus()));
        }
        if (request.getCategoryId() != null) {
            // Reading the id through the field, not through the join, so the count query can reuse this.
            predicates.add(cb.equal(product.get(Product_.category).get(Category_.id), request.getCategoryId()));
        }
        if (request.getMinPrice() != null) {
            predicates.add(cb.greaterThanOrEqualTo(product.get(Product_.price), request.getMinPrice()));
        }
        if (request.getMaxPrice() != null) {
            predicates.add(cb.lessThanOrEqualTo(product.get(Product_.price), request.getMaxPrice()));
        }
        if (Boolean.TRUE.equals(request.getInStock())) {
            predicates.add(cb.greaterThan(product.get(Product_.stockQuantity), 0));
        }

        return predicates.toArray(new Predicate[0]);
    }
}
