package com.example.project2.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project2.dto.response.CacheDemoReportResponse;
import com.example.project2.entity.Category;
import com.example.project2.service.CacheDemoService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Interview topic: docs/interview/spring-data-jpa/01-first-level-vs-second-level-cache.md#measuring-it
 * Loads the same {@link Category} across two genuinely separate transactions and counts SQL
 * statements with Hibernate's {@link Statistics}, the same technique {@code NPlusOneDemoServiceImpl}
 * uses - this is a diagnostic tool, not a benchmark.
 *
 * <p>Two {@code REQUIRES_NEW} transactions, not one method plus {@code entityManager.clear()}: L2's
 * read-write strategy stamps every cached entry with the caching transaction's own timestamp and
 * refuses to read it back inside that same transaction (see
 * {@code org.hibernate.cache.spi.support.AbstractReadWriteAccess#get}, "items created after the
 * start of this transaction" are not readable). Clearing the persistence context only empties L1;
 * only a new transaction's later timestamp makes the L2 entry visible - which is exactly what a
 * second, later HTTP request would look like in production.
 */
@Slf4j
@Service
public class CacheDemoServiceImpl implements CacheDemoService {

    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;
    private final TransactionTemplate newTransaction;

    public CacheDemoServiceImpl(EntityManager entityManager, EntityManagerFactory entityManagerFactory,
                                 PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.entityManagerFactory = entityManagerFactory;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public CacheDemoReportResponse run(Long categoryId) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        // Clean slate: an earlier call to this same endpoint may already have put this id into L2.
        entityManagerFactory.getCache().evict(Category.class, categoryId);

        long hitsBefore = statistics.getSecondLevelCacheHitCount();
        long missesBefore = statistics.getSecondLevelCacheMissCount();

        long statementsBeforeFirstLoad = statistics.getPrepareStatementCount();
        FirstTransactionResult firstTx = newTransaction.execute(status -> loadTwiceInOneTransaction(categoryId));
        long statementsAfterL1Repeat = statistics.getPrepareStatementCount();

        // A brand-new transaction - the only way to actually exercise the L2 read path.
        long statementsBeforeL2Load = statistics.getPrepareStatementCount();
        Category afterNewTransaction = newTransaction.execute(
                status -> entityManager.find(Category.class, categoryId));
        long statementsAfterL2Load = statistics.getPrepareStatementCount();

        log.debug("Cache demo for category {}: firstLoad={}, l1Repeat={}, newTxLoad={} statements",
                categoryId,
                firstTx.statementsForFirstLoad(),
                statementsAfterL1Repeat - (statementsBeforeFirstLoad + firstTx.statementsForFirstLoad()),
                statementsAfterL2Load - statementsBeforeL2Load);

        return CacheDemoReportResponse.builder()
                .categoryId(categoryId)
                .sqlStatementsFirstLoad(firstTx.statementsForFirstLoad())
                .sqlStatementsL1Repeat(firstTx.statementsForL1Repeat())
                .sqlStatementsAfterClearL2Hit(statementsAfterL2Load - statementsBeforeL2Load)
                .sameInstanceWithinSession(firstTx.first() == firstTx.repeat())
                .sameInstanceAfterClear(firstTx.first() == afterNewTransaction)
                .secondLevelCacheHitCount(statistics.getSecondLevelCacheHitCount() - hitsBefore)
                .secondLevelCacheMissCount(statistics.getSecondLevelCacheMissCount() - missesBefore)
                .build();
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/01-first-level-vs-second-level-cache.md#first-level-cache
     * Both loads share one persistence context, so the second one never leaves the JVM: same
     * managed instance, no extra SQL, no L2 lookup at all.
     */
    private FirstTransactionResult loadTwiceInOneTransaction(Long categoryId) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long before = statistics.getPrepareStatementCount();

        Category first = entityManager.find(Category.class, categoryId);
        if (first == null) {
            throw BusinessException.notFound("Category not found with id: " + categoryId);
        }
        long afterFirst = statistics.getPrepareStatementCount();

        Category repeat = entityManager.find(Category.class, categoryId);
        long afterRepeat = statistics.getPrepareStatementCount();

        return new FirstTransactionResult(first, repeat, afterFirst - before, afterRepeat - afterFirst);
    }

    private record FirstTransactionResult(Category first, Category repeat,
                                           long statementsForFirstLoad, long statementsForL1Repeat) {
    }
}
