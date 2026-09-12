package com.example.project2.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#jointable-many-to-many
 * The far end of the product_tags join table, deliberately without a products collection.
 */
@Entity
@Table(name = "tags")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tag {

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#id-and-generatedvalue
     * A sequence hands out ids before the insert, so Hibernate can batch 50 tags into one round trip.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tag_seq")
    @SequenceGenerator(name = "tag_seq", sequenceName = "tags_seq", allocationSize = 50)
    private Long id;

    @Column(name = "name", length = 100, nullable = false, unique = true)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#equals-hashcode-and-tostring
     * Product holds tags in a Set, so the Set needs these two to behave.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Tag that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    /** Constant on purpose, so a tag does not change bucket when it gets its id. */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
