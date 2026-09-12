package com.example.project2.repository;

import com.example.project2.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#jointable-many-to-many
 * Tags are looked up by name so the same tag is reused instead of duplicated per product.
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByNameIgnoreCase(String name);

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#jointable-many-to-many
     * Queries the join table from the tag end, which is why Tag needs no products collection.
     */
    @Query("select t from Product p join p.tags t where p.id = :productId order by t.name")
    List<Tag> findByProductId(@Param("productId") Long productId);
}
