package com.example.project2.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Interview topic: docs/interview/spring-data-jpa/01-first-level-vs-second-level-cache.md#second-level-cache
 * (this is the L2-cached entity the doc's comparison and CacheDemoServiceImpl are built around)
 *
 * <p>READ_WRITE locks the cache entry for the duration of a commit, so a reader can never observe
 * this instance's own uncommitted write - the trade-off is a lock/unlock pair on every write.
 * Category data changes rarely enough (an admin screen, not checkout) that the cost is worth it.
 */
@Entity
@Table(name = "categories")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "categoryCache")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "parent_id")
    private Long parentId;

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#read-only-joincolumn
     * Read-only because parentId above already writes this column, and Hibernate will not start with two writable mappings of one column.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    private Category parent;

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#inverse-side-with-mappedby
     * No cascade here, because deleting a category must never silently delete its subtree.
     */
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Category> children = new ArrayList<>();

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "slug", length = 200, unique = true)
    private String slug;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "level")
    @Builder.Default
    private Integer level = 0;

    @Column(name = "is_featured")
    @Builder.Default
    private Boolean isFeatured = false;

    @Column(name = "meta_title", length = 200)
    private String metaTitle;

    @Column(name = "meta_description", columnDefinition = "TEXT")
    private String metaDescription;

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
}
