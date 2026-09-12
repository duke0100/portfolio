package com.example.project2.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project2.dto.request.AddProductTagsRequest;
import com.example.project2.dto.request.CreateProductReviewRequest;
import com.example.project2.dto.response.CategoryTreeResponse;
import com.example.project2.dto.response.ProductReviewResponse;
import com.example.project2.dto.response.ProductTagsResponse;
import com.example.project2.dto.response.RelationshipDemoReportResponse;
import com.example.project2.entity.Category;
import com.example.project2.entity.Product;
import com.example.project2.entity.ProductReview;
import com.example.project2.entity.ReviewStatus;
import com.example.project2.entity.Tag;
import com.example.project2.repository.CategoryRepository;
import com.example.project2.repository.ProductRepository;
import com.example.project2.repository.ProductReviewRepository;
import com.example.project2.repository.TagRepository;
import com.example.project2.service.RelationshipService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.List;

/**
 * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#seeing-it-fail
 * Every scenario here runs in its own transaction and is rolled back, so the table never grows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationshipServiceImpl implements RelationshipService {

    private static final String OK = "OK";

    private final ProductRepository productRepository;
    private final ProductReviewRepository productReviewRepository;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#syncing-both-sides
     * Saves the parent rather than the review, because cascade is what inserts the child here.
     */
    @Override
    @Transactional
    public ProductReviewResponse addReview(Long productId, CreateProductReviewRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("Product not found with id: " + productId));

        ProductReview review = ProductReview.builder()
                .authorName(request.getAuthorName())
                .rating(request.getRating())
                .title(request.getTitle())
                .comment(request.getComment())
                .build();

        product.addReview(review);
        productRepository.saveAndFlush(product);
        log.debug("Added review {} to product {}", review.getId(), productId);

        return toResponse(review, product.getName());
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#reading-the-collection
     * The flag picks between one statement with the product and one without it.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProductReviewResponse> findReviews(Long productId, boolean fetchProduct) {
        if (fetchProduct) {
            return productReviewRepository.findWithProductByProductId(productId).stream()
                    .map(review -> toResponse(review, review.getProduct().getName()))
                    .toList();
        }
        // The product stays a proxy, so asking for its name here would cost one query per review.
        return productReviewRepository.findByProductIdOrderByIdAsc(productId).stream()
                .map(review -> toResponse(review, null))
                .toList();
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#enumerated-and-transient
     * Spring converts the query parameter to the enum, so an unknown status is rejected before this runs.
     */
    @Override
    @Transactional
    public ProductReviewResponse updateReviewStatus(Long productId, Long reviewId, ReviewStatus status) {
        ProductReview review = productReviewRepository.findById(reviewId)
                .orElseThrow(() -> BusinessException.notFound("Review not found with id: " + reviewId));

        if (!productId.equals(review.getProduct().getId())) {
            throw BusinessException.badRequest("Review " + reviewId + " does not belong to product " + productId);
        }

        review.setStatus(status);
        productReviewRepository.saveAndFlush(review);
        return toResponse(review, null);
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#inverse-side-with-mappedby
     * Removing the review from the list is the delete, which is why no repository call is needed.
     */
    @Override
    @Transactional
    public void deleteReview(Long productId, Long reviewId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("Product not found with id: " + productId));

        ProductReview review = product.getReviews().stream()
                .filter(candidate -> reviewId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("Review not found with id: " + reviewId));

        product.removeReview(review);
        productRepository.saveAndFlush(product);
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#jointable-many-to-many
     * Tags are saved first because the many-to-many has no cascade, then adding them writes the join rows.
     */
    @Override
    @Transactional
    public ProductTagsResponse addTags(Long productId, AddProductTagsRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("Product not found with id: " + productId));

        for (String name : request.getNames()) {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Tag tag = tagRepository.findByNameIgnoreCase(trimmed)
                    .orElseGet(() -> tagRepository.save(Tag.builder().name(trimmed).build()));
            product.getTags().add(tag);
        }

        productRepository.saveAndFlush(product);
        log.debug("Product {} now has {} tags", productId, product.getTags().size());
        return toTagsResponse(product);
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#jointable-many-to-many
     * Reads the tags with one join over product_tags instead of loading the collection lazily.
     */
    @Override
    @Transactional(readOnly = true)
    public ProductTagsResponse findTags(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("Product not found with id: " + productId));

        List<String> names = tagRepository.findByProductId(productId).stream()
                .map(Tag::getName)
                .toList();

        return ProductTagsResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .effectivePrice(product.getEffectivePrice())
                .tagNames(names)
                .tagCount(names.size())
                .build();
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#read-only-joincolumn
     * Reads the writable parentId column and the read-only parent association side by side.
     */
    @Override
    @Transactional(readOnly = true)
    public CategoryTreeResponse findCategoryTree(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> BusinessException.notFound("Category not found with id: " + categoryId));

        return CategoryTreeResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .parentId(category.getParentId())
                .parentName(category.getParent() != null ? category.getParent().getName() : null)
                .childNames(category.getChildren().stream().map(Category::getName).toList())
                .childCount(category.getChildren().size())
                .build();
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#seeing-it-fail
     * The scenario table in that section is this method's output.
     */
    @Override
    @Transactional
    public RelationshipDemoReportResponse runScenario(Scenario scenario, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("Product not found with id: " + productId));

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        // Load the list first, so the staleness check below is about the write and not about the load.
        int sizeBefore = product.getReviews().size();
        long statementsBefore = statistics.getPrepareStatementCount();

        ProductReview review = ProductReview.builder()
                .authorName("relationship-demo")
                .rating(5)
                .title("demo")
                .comment("Written by the relationship demo endpoint, then rolled back.")
                .build();

        String note;
        String outcome = OK;
        String error = null;
        try {
            note = switch (scenario) {
                case OWNING_SIDE_ONLY -> owningSideOnly(product, review);
                case INVERSE_SIDE_ONLY -> inverseSideOnly(product, review);
                case BOTH_SIDES_SYNCED -> bothSidesSynced(product, review);
                case ORPHAN_REMOVAL -> orphanRemoval(product, review);
            };
        } catch (RuntimeException ex) {
            outcome = "FAILED";
            note = "The flush was rejected - see error.";
            error = rootMessage(ex);
            log.debug("Scenario {} failed as expected: {}", scenario, error);
        }

        long statements = statistics.getPrepareStatementCount() - statementsBefore;
        // A failed flush leaves the persistence context unusable, so only count rows when it worked.
        long rowsInDb = OK.equals(outcome) ? productReviewRepository.countByProductId(productId) : -1;

        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

        return RelationshipDemoReportResponse.builder()
                .scenario(scenario.name())
                .note(note + " Reviews in memory before: " + sizeBefore + ".")
                .outcome(outcome)
                .error(error)
                .reviewsInMemory(OK.equals(outcome) ? product.getReviews().size() : sizeBefore)
                .reviewsInDatabase(rowsInDb)
                .sqlStatements(statements)
                .rolledBack(true)
                .build();
    }

    /** The row is written, but nobody updates the parent's list for us. */
    private String owningSideOnly(Product product, ProductReview review) {
        review.setProduct(product);
        productReviewRepository.saveAndFlush(review);
        return "Set review.product and saved the review. Row written, product.reviews stale.";
    }

    /** Cascade inserts the review, but nothing ever fills in product_id. */
    private String inverseSideOnly(Product product, ProductReview review) {
        product.getReviews().add(review);
        entityManager.flush();
        return "Added to product.reviews without setting review.product.";
    }

    /** The helper writes both ends, which is the only version correct in a single call. */
    private String bothSidesSynced(Product product, ProductReview review) {
        product.addReview(review);
        entityManager.flush();
        return "Used product.addReview(...). Row written and product.reviews correct.";
    }

    /** Dropping the review from the list is what makes Hibernate issue the delete. */
    private String orphanRemoval(Product product, ProductReview review) {
        product.addReview(review);
        entityManager.flush();
        product.removeReview(review);
        entityManager.flush();
        return "Added, flushed, then removed from the collection - orphanRemoval deleted it.";
    }

    private ProductReviewResponse toResponse(ProductReview review, String productName) {
        return ProductReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProduct() != null ? review.getProduct().getId() : null)
                .productName(productName)
                .authorName(review.getAuthorName())
                .rating(review.getRating())
                .status(review.getStatus() != null ? review.getStatus().name() : null)
                .title(review.getTitle())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }

    private ProductTagsResponse toTagsResponse(Product product) {
        List<String> names = product.getTags().stream()
                .map(Tag::getName)
                .sorted()
                .toList();

        return ProductTagsResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .effectivePrice(product.getEffectivePrice())
                .tagNames(names)
                .tagCount(names.size())
                .build();
    }

    private String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
