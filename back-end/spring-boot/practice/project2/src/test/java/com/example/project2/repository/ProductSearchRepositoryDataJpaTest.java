package com.example.project2.repository;

import com.example.project2.dto.request.ProductSearchRequest;
import com.example.project2.dto.response.ProductSearchResultResponse;
import com.example.project2.entity.Category;
import com.example.project2.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#comparison
 * The JPQL and Criteria searches run on H2 with no changes - that is the "any database" part.
 * {@code searchWithFullText} cannot be tested here, because {@code to_tsvector} is PostgreSQL only.
 */
@DataJpaTest
class ProductSearchRepositoryDataJpaTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 10, Sort.by("id"));

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductRepository productRepository;

    private Long peripheralsId;

    @BeforeEach
    void seed() {
        Category peripherals = entityManager.persistAndFlush(Category.builder().name("Peripherals").build());
        peripheralsId = peripherals.getId();

        entityManager.persist(Product.builder().category(peripherals).name("Wireless Mouse")
                .brand("Acme").price(BigDecimal.valueOf(25)).stockQuantity(4).sku("MS-100").build());
        entityManager.persist(Product.builder().category(peripherals).name("Wired Mouse")
                .brand("Globex").price(BigDecimal.valueOf(9)).stockQuantity(0).sku("MS-101").build());
        entityManager.persist(Product.builder().name("Standing Desk")
                .brand("Acme").price(BigDecimal.valueOf(400)).stockQuantity(2).sku("DK-100").build());
        entityManager.flush();
    }

    @Test
    void criteriaSearch_withNoFilters_returnsEverything() {
        Page<ProductSearchResultResponse> page =
                productRepository.searchWithCriteria(ProductSearchRequest.builder().build(), FIRST_PAGE);

        assertThat(page.getTotalElements()).isEqualTo(3);
        // The left join must keep the product that has no category; an inner join would drop it.
        assertThat(page.getContent()).anyMatch(row -> row.getCategoryName() == null);
    }

    @Test
    void criteriaSearch_appliesOnlyTheFiltersThatWereSet() {
        ProductSearchRequest request = ProductSearchRequest.builder()
                .name("mouse")
                .brand("Acme")
                .inStock(true)
                .build();

        Page<ProductSearchResultResponse> page = productRepository.searchWithCriteria(request, FIRST_PAGE);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getName()).isEqualTo("Wireless Mouse");
        assertThat(page.getContent().getFirst().getCategoryName()).isEqualTo("Peripherals");
    }

    @Test
    void jpqlSearch_andCriteriaSearch_agreeOnTheSameFilters() {
        Page<ProductSearchResultResponse> jpql = productRepository.searchWithJpql(
                "mouse", null, null, peripheralsId, BigDecimal.ZERO, BigDecimal.valueOf(50), false, FIRST_PAGE);
        Page<ProductSearchResultResponse> criteria = productRepository.searchWithCriteria(
                ProductSearchRequest.builder()
                        .name("mouse")
                        .categoryId(peripheralsId)
                        .minPrice(BigDecimal.ZERO)
                        .maxPrice(BigDecimal.valueOf(50))
                        .build(),
                FIRST_PAGE);

        assertThat(jpql.getTotalElements()).isEqualTo(2);
        assertThat(jpql.getContent()).extracting(ProductSearchResultResponse::getName)
                .containsExactlyElementsOf(criteria.getContent().stream()
                        .map(ProductSearchResultResponse::getName)
                        .toList());
    }

    @Test
    void jpqlSearch_withEveryFilterNull_appliesNoFiltering() {
        Page<ProductSearchResultResponse> page = productRepository.searchWithJpql(
                null, null, null, null, null, null, false, FIRST_PAGE);

        assertThat(page.getTotalElements()).isEqualTo(3);
    }
}
