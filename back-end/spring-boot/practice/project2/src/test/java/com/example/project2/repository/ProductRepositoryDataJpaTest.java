package com.example.project2.repository;

import com.example.project2.entity.Category;
import com.example.project2.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interview topic: docs/interview/spring-boot/02-test-slices-vs-mockito.md#datajpatest
 * Only starts the JPA layer against an embedded H2 database - no controller, no service. Each
 * test runs in its own rolled-back transaction, so tests never see each other's data.
 */
@DataJpaTest
class ProductRepositoryDataJpaTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void save_thenFindBySku_returnsPersistedProduct() {
        Category category = entityManager.persistAndFlush(Category.builder().name("Peripherals").build());
        Product product = Product.builder()
                .category(category)
                .name("Mechanical Keyboard")
                .price(BigDecimal.valueOf(89.90))
                .sku("KB-001")
                .build();
        entityManager.persistAndFlush(product);

        var found = productRepository.findBySku("KB-001");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Mechanical Keyboard");
        assertThat(found.get().getCreatedAt()).isNotNull(); // set by @PrePersist, proves it really hit the DB
    }

    @Test
    void existsBySkuAndIdNot_excludesTheProductBeingUpdated() {
        Product product = entityManager.persistAndFlush(
                Product.builder().name("Mouse").price(BigDecimal.ONE).sku("MS-001").build());

        boolean clashesWithItself = productRepository.existsBySkuAndIdNot("MS-001", product.getId());
        boolean clashesWithAnotherId = productRepository.existsBySkuAndIdNot("MS-001", product.getId() + 1);

        assertThat(clashesWithItself).isFalse();
        assertThat(clashesWithAnotherId).isTrue();
    }
}
