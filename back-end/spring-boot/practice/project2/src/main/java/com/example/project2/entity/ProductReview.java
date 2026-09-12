package com.example.project2.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#owning-side
 * The product_id column lives in this table, so this is the only side that can write it.
 */
@Entity
@Table(name = "product_reviews")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReview {

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#id-and-generatedvalue
     * IDENTITY makes Postgres hand out the id during the insert, which is why these inserts cannot be batched.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#joincolumn-attributes
     * Spelled out as LAZY because the default would load the whole product on every review read.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            referencedColumnName = "id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_reviews_product"))
    private Product product;

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#enumerated-and-transient
     * Stored as text, so reordering the enum constants later cannot change what old rows mean.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(name = "author_name", length = 150, nullable = false)
    private String authorName;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#equals-hashcode-and-tostring
     * Compares ids only and never reads product, so it cannot walk back into the parent.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductReview that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    /** A constant hash keeps the review in the same bucket after Hibernate assigns its id. */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
