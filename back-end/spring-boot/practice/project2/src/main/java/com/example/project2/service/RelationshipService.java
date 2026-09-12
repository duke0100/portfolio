package com.example.project2.service;

import com.example.project2.dto.request.AddProductTagsRequest;
import com.example.project2.dto.request.CreateProductReviewRequest;
import com.example.project2.dto.response.CategoryTreeResponse;
import com.example.project2.dto.response.ProductReviewResponse;
import com.example.project2.dto.response.ProductTagsResponse;
import com.example.project2.dto.response.RelationshipDemoReportResponse;
import com.example.project2.entity.ReviewStatus;

import java.util.List;

/**
 * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#seeing-it-fail
 * Read and write endpoints over the Product mappings, plus four scenarios that show which side owns the FK.
 */
public interface RelationshipService {

    /** Which side of the association the scenario writes. */
    enum Scenario {
        /** Sets the review's product only, so the row is written but the parent's list is stale. */
        OWNING_SIDE_ONLY,
        /** Adds to the product's list only, so product_id is null and the insert is rejected. */
        INVERSE_SIDE_ONLY,
        /** Uses the helper, so the row is written and the list is right. */
        BOTH_SIDES_SYNCED,
        /** Removes the review from the list, which is what orphanRemoval turns into a delete. */
        ORPHAN_REMOVAL
    }

    /** Writes a review through the parent, so cascade inserts it and both ends stay in sync. */
    ProductReviewResponse addReview(Long productId, CreateProductReviewRequest request);

    /** Pass fetchProduct true to join fetch the product, false to leave it a lazy proxy. */
    List<ProductReviewResponse> findReviews(Long productId, boolean fetchProduct);

    /** Moderates a review, which is the one place the status enum is written. */
    ProductReviewResponse updateReviewStatus(Long productId, Long reviewId, ReviewStatus status);

    /** Deletes through the collection, which is what orphanRemoval is for. */
    void deleteReview(Long productId, Long reviewId);

    /** Attaches tags to a product, creating any name that does not exist yet. */
    ProductTagsResponse addTags(Long productId, AddProductTagsRequest request);

    /** Reads the tags back out of the join table. */
    ProductTagsResponse findTags(Long productId);

    /** Reads the self-referencing category association in both directions. */
    CategoryTreeResponse findCategoryTree(Long categoryId);

    /** Runs one scenario and rolls it back, so the demo leaves no rows behind. */
    RelationshipDemoReportResponse runScenario(Scenario scenario, Long productId);
}
