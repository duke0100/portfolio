package com.example.project2.repository;

import com.example.project2.entity.Category;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interview topic: docs/interview/spring-data-jpa/01-first-level-vs-second-level-cache.md#measuring-it
 * Same technique as {@code OrderRepositoryNPlusOneDataJpaTest}: count real SQL statements with
 * Hibernate's {@link Statistics} instead of asserting on timing. Mirrors {@code CacheDemoServiceImpl}
 * directly against the repository/entity manager so the L1 vs L2 behaviour is provable with H2
 * alone - no Postgres, no running app needed.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class CategoryRepositoryCacheDataJpaTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private Long categoryId;

    @BeforeEach
    void seed() {
        Category category = entityManager.persistAndFlush(Category.builder().name("Peripherals").build());
        categoryId = category.getId();
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        // Commit the insert as its own transaction before the tests below start their own -
        // see the two tests for why the transaction boundary itself matters here.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        entityManager.clear();
        entityManagerFactory.getCache().evict(Category.class, categoryId);
    }

    @Test
    void findById_repeatedInSameTransaction_hitsL1AndFiresNoExtraSql() {
        long statementsBefore = statistics.getPrepareStatementCount();

        Category first = categoryRepository.findById(categoryId).orElseThrow();
        long statementsAfterFirst = statistics.getPrepareStatementCount();
        Category second = categoryRepository.findById(categoryId).orElseThrow();
        long statementsAfterSecond = statistics.getPrepareStatementCount();

        assertThat(statementsAfterFirst - statementsBefore).isEqualTo(1); // L1 + L2 both miss
        assertThat(statementsAfterSecond - statementsAfterFirst).isEqualTo(0); // served from L1
        assertThat(first).isSameAs(second); // same object reference, not just equal data
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/01-first-level-vs-second-level-cache.md#pitfalls
     * {@code entityManager.clear()} alone would NOT be enough here: Hibernate's read-write L2
     * strategy stamps a cached entry with its writing transaction's own timestamp and refuses to
     * read it back inside that same transaction. A second, later transaction is required - which is
     * exactly what {@code TestTransaction.end()}/{@code start()} simulates.
     */
    @Test
    void findById_inANewTransaction_hitsL2AndFiresNoSql() {
        long hitsBefore = statistics.getSecondLevelCacheHitCount();

        Category first = categoryRepository.findById(categoryId).orElseThrow(); // L2 miss, then populates L2

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start(); // a later transaction - only now is the L2 entry readable

        long statementsBeforeSecond = statistics.getPrepareStatementCount();
        Category second = categoryRepository.findById(categoryId).orElseThrow();
        long statementsAfterSecond = statistics.getPrepareStatementCount();

        assertThat(statementsAfterSecond - statementsBeforeSecond).isEqualTo(0); // served from L2, no SQL
        assertThat(statistics.getSecondLevelCacheHitCount() - hitsBefore).isEqualTo(1);
        assertThat(first).isNotSameAs(second); // a new instance, rebuilt from the cached state
    }
}
