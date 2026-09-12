package com.example.project2.repository;

import com.example.project2.entity.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#reading-the-collection
 * Two ways to read the children, one leaving the parent a proxy and one fetching it.
 */
@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    /** Cheapest way to render a review list, since the product is never loaded. */
    List<ProductReview> findByProductIdOrderByIdAsc(Long productId);

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#reading-the-collection
     * Reviews and product in one statement, so reading the product name afterwards is free.
     */
    @Query("""
            select distinct r
            from ProductReview r
            left join fetch r.product p
            where p.id = :productId
            """)
    List<ProductReview> findWithProductByProductId(@Param("productId") Long productId);

    long countByProductId(Long productId);
}
