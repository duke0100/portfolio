package com.example.project2.repository;

import com.example.project2.entity.Order;
import com.example.project2.entity.OrderDetail;
import com.example.project2.entity.Product;
import com.example.project2.entity.User;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

import jakarta.persistence.EntityManagerFactory;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#measuring-it
 * Turns "this query feels slow" into a real assertion by counting SQL statements. Uses 5 orders
 * with distinct buyers so H2 (no need for real Postgres) can prove the query counts, not the data.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class OrderRepositoryNPlusOneDataJpaTest {

    private static final int ORDERS = 5;
    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20, Sort.by("id"));

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void seed() {
        Product product = entityManager.persistAndFlush(Product.builder()
                .name("Widget")
                .price(BigDecimal.TEN)
                .sku("W-1")
                .build());

        for (int i = 0; i < ORDERS; i++) {
            // A different buyer per order - otherwise the cache would hide the N+1 we're testing for.
            User buyer = entityManager.persistAndFlush(User.builder()
                    .username("buyer-" + i)
                    .email("buyer-" + i + "@example.com")
                    .passwordHash("not-a-real-hash")
                    .build());
            Order order = entityManager.persistAndFlush(Order.builder()
                    .user(buyer)
                    .orderNumber("ORD-" + i)
                    .status("PENDING")
                    .totalAmount(BigDecimal.TEN)
                    .build());
            for (int line = 0; line < 2; line++) {
                entityManager.persistAndFlush(OrderDetail.builder()
                        .order(order)
                        .product(product)
                        .quantity(1)
                        .unitPrice(BigDecimal.TEN)
                        .totalPrice(BigDecimal.TEN)
                        .build());
            }
        }

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void findByStatus_withoutFetch_firesOneSelectPerRow() {
        long statements = countStatements(() -> orderRepository.findByStatus("PENDING", FIRST_PAGE)
                .getContent()
                .forEach(order -> order.getUser().getEmail()));

        assertThat(statements).isEqualTo(1 + ORDERS); // the N+1, measured
    }

    @Test
    void findByStatusJoinFetchUser_firesOneSelect() {
        long statements = countStatements(() -> orderRepository.findByStatusJoinFetchUser("PENDING", FIRST_PAGE)
                .getContent()
                .forEach(order -> order.getUser().getEmail()));

        assertThat(statements).isEqualTo(1);
    }

    @Test
    void findWithUserByStatus_entityGraph_firesOneSelect() {
        long statements = countStatements(() -> orderRepository.findWithUserByStatus("PENDING", FIRST_PAGE)
                .getContent()
                .forEach(order -> order.getUser().getEmail()));

        assertThat(statements).isEqualTo(1);
    }

    @Test
    void findSummaryByStatus_projection_firesOneSelect() {
        long statements = countStatements(() -> {
            var page = orderRepository.findSummaryByStatus("PENDING", FIRST_PAGE);
            assertThat(page.getContent()).hasSize(ORDERS);
            assertThat(page.getContent().getFirst().getUserEmail()).isEqualTo("buyer-0@example.com");
        });

        assertThat(statements).isEqualTo(1);
    }

    @Test
    void touchingDetails_withBatchSize_batchesTheCollectionLoads() {
        long collectionsBefore = statistics.getCollectionFetchCount();

        long statements = countStatements(() -> orderRepository.findWithUserByStatus("PENDING", FIRST_PAGE)
                .getContent()
                .forEach(order -> order.getDetails().size()));

        // 1 select for the page + 1 batched select covering all 5 orders' details.
        assertThat(statements).isEqualTo(2);
        // One batched round trip loaded all 5 collections, so the fetch count is 1, not 5.
        assertThat(statistics.getCollectionFetchCount() - collectionsBefore).isEqualTo(1);
    }

    @Test
    void idsThenFetch_paginatesInSqlAndFetchesTheGraphInOneMoreQuery() {
        long collectionsBefore = statistics.getCollectionFetchCount();

        long statements = countStatements(() -> {
            List<Long> ids = orderRepository.findIdsByStatus("PENDING", FIRST_PAGE).getContent();
            List<Order> orders = orderRepository.findWithUserAndDetailsByIdIn(ids);
            assertThat(orders).hasSize(ORDERS);
            orders.forEach(order -> {
                order.getUser().getEmail();
                assertThat(order.getDetails()).hasSize(2);
            });
        });

        assertThat(statements).isEqualTo(2);
        // Collections were already joined in, so there's no separate fetch afterwards.
        assertThat(statistics.getCollectionFetchCount() - collectionsBefore).isEqualTo(0);
    }

    /** Clears the persistence context, then measures how many SQL statements the given work fires. */
    private long countStatements(Runnable work) {
        entityManager.flush();
        entityManager.clear();
        long before = statistics.getPrepareStatementCount();
        work.run();
        return statistics.getPrepareStatementCount() - before;
    }
}
