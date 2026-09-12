package com.example.project2.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.request.AddProductTagsRequest;
import com.example.project2.dto.request.CreateProductReviewRequest;
import com.example.project2.dto.response.CategoryTreeResponse;
import com.example.project2.dto.response.ProductReviewResponse;
import com.example.project2.dto.response.ProductTagsResponse;
import com.example.project2.dto.response.RelationshipDemoReportResponse;
import com.example.project2.entity.ReviewStatus;
import com.example.project2.service.RelationshipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#seeing-it-fail
 * Kept under its own path so these demo routes stay out of ProductController's namespace.
 */
@RestController
@RequestMapping("/api/v1/relationships")
@RequiredArgsConstructor
public class RelationshipController {

    private final RelationshipService relationshipService;

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#syncing-both-sides
     * Writes the review through the product, so both ends of the association are set.
     */
    @PostMapping("/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<ProductReviewResponse>> addReview(
            @PathVariable Long productId,
            @Valid @RequestBody CreateProductReviewRequest request) {
        ProductReviewResponse response = relationshipService.addReview(productId, request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#reading-the-collection
     * Pass fetchProduct=true for the join fetch, false to leave the product a proxy.
     */
    @GetMapping("/products/{productId}/reviews")
    public ApiResponse<List<ProductReviewResponse>> findReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "false") boolean fetchProduct) {
        return ApiResponse.ok(relationshipService.findReviews(productId, fetchProduct));
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#enumerated-and-transient
     * A status outside the enum is rejected as a bad request before the service is called.
     */
    @PatchMapping("/products/{productId}/reviews/{reviewId}/status")
    public ApiResponse<ProductReviewResponse> updateReviewStatus(
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @RequestParam ReviewStatus status) {
        return ApiResponse.ok(relationshipService.updateReviewStatus(productId, reviewId, status));
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#inverse-side-with-mappedby
     * Deletes through the collection instead of through a repository.
     */
    @DeleteMapping("/products/{productId}/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long productId, @PathVariable Long reviewId) {
        relationshipService.deleteReview(productId, reviewId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#jointable-many-to-many
     * Each name added here becomes one row in product_tags.
     */
    @PostMapping("/products/{productId}/tags")
    public ApiResponse<ProductTagsResponse> addTags(
            @PathVariable Long productId,
            @Valid @RequestBody AddProductTagsRequest request) {
        return ApiResponse.ok(relationshipService.addTags(productId, request));
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#jointable-many-to-many
     * Reads the tags back through the join table.
     */
    @GetMapping("/products/{productId}/tags")
    public ApiResponse<ProductTagsResponse> findTags(@PathVariable Long productId) {
        return ApiResponse.ok(relationshipService.findTags(productId));
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#read-only-joincolumn
     * Parent comes from the read-only association, children from the mappedBy side.
     */
    @GetMapping("/categories/{categoryId}/tree")
    public ApiResponse<CategoryTreeResponse> findCategoryTree(@PathVariable Long categoryId) {
        return ApiResponse.ok(relationshipService.findCategoryTree(categoryId));
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#seeing-it-fail
     * One scenario per call, for example ?scenario=INVERSE_SIDE_ONLY&productId=1.
     */
    @GetMapping("/demo")
    public ApiResponse<RelationshipDemoReportResponse> runScenario(
            @RequestParam RelationshipService.Scenario scenario,
            @RequestParam Long productId) {
        return ApiResponse.ok(relationshipService.runScenario(scenario, productId));
    }
}
