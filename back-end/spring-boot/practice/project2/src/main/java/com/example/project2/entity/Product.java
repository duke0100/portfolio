package com.example.project2.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#unidirectional-vs-bidirectional
     * Category has no products list on purpose, because with ~1M rows nobody should be able to load them by walking the graph.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "name", length = 300, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", precision = 15, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "stock_quantity")
    @Builder.Default
    private Integer stockQuantity = 0;

    @Column(name = "sku", length = 100, unique = true)
    private String sku;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "weight", precision = 10, scale = 3)
    private BigDecimal weight;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "brand", length = 100)
    private String brand;

    @Column(name = "dimensions", length = 100)
    private String dimensions;

    @Column(name = "discount_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /**
     * Interview topic: docs/interview/database/01-database-migrations.md#versioned-migration
     * (this column is the expand/migrate/contract example added by
     * V6__add_low_stock_threshold_to_products.sql; the NOT NULL + DEFAULT 5 only land in the
     * changeset run after this field is already deployed and writing the column on every insert)
     */
    @Column(name = "low_stock_threshold", nullable = false)
    @Builder.Default
    private Integer lowStockThreshold = 5;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#inverse-side-with-mappedby
     * ProductReview owns the FK, so this side gets no column of its own and no join table.
     */
    @OneToMany(
            mappedBy = "product",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductReview> reviews = new ArrayList<>();

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#jointable-many-to-many
     * This side owns product_tags, so adding a tag here is what writes the join-table row.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "product_tags",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"),
            foreignKey = @ForeignKey(name = "fk_product_tags_product"),
            inverseForeignKey = @ForeignKey(name = "fk_product_tags_tag"))
    @Builder.Default
    private Set<Tag> tags = new LinkedHashSet<>();

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#enumerated-and-transient
     * There is no column behind this, so changing the discount formula needs no migration.
     */
    @Transient
    private BigDecimal effectivePrice;

    /** Works out the discounted price once the row has been read. */
    @PostLoad
    void computeEffectivePrice() {
        if (price == null) {
            return;
        }
        BigDecimal percent = discountPercent != null ? discountPercent : BigDecimal.ZERO;
        BigDecimal factor = BigDecimal.ONE.subtract(percent.divide(BigDecimal.valueOf(100)));
        this.effectivePrice = price.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#syncing-both-sides
     * One call sets both ends, because setting only one of them either fails or leaves stale data behind.
     */
    public void addReview(ProductReview review) {
        reviews.add(review);
        review.setProduct(this);
    }

    /** Detaching the review here is what makes orphanRemoval delete the row on flush. */
    public void removeReview(ProductReview review) {
        reviews.remove(review);
        review.setProduct(null);
    }
}
