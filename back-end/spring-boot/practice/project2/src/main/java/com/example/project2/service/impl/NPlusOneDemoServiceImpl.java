package com.example.project2.service.impl;

import com.example.project2.dto.response.NPlusOneReportResponse;
import com.example.project2.dto.response.OrderSummaryResponse;
import com.example.project2.entity.Order;
import com.example.project2.repository.OrderRepository;
import com.example.project2.service.NPlusOneDemoService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#measuring-it
 * Runs the same query six different ways and counts how many SQL statements each one actually
 * fires, using Hibernate's own {@link Statistics}. This is a teaching/diagnostic tool, not a
 * precise benchmark - concurrent traffic on the same instance would skew the counts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NPlusOneDemoServiceImpl implements NPlusOneDemoService {

    private static final String DEFAULT_STATUS = "PENDING";

    private final OrderRepository orderRepository;
    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;

    @Override
    @Transactional(readOnly = true)
    public NPlusOneReportResponse run(Strategy strategy, String status, Pageable pageable) {
        String effectiveStatus = status != null ? status : DEFAULT_STATUS;
        log.debug("Running N+1 demo strategy {} for status {}", strategy, effectiveStatus);
        return measure(strategy, effectiveStatus, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NPlusOneReportResponse> compare(String status, Pageable pageable) {
        String effectiveStatus = status != null ? status : DEFAULT_STATUS;
        return List.of(Strategy.values()).stream()
                .map(strategy -> measure(strategy, effectiveStatus, pageable))
                .toList();
    }

    /** Measures one strategy's SQL statement count, before vs. after running it. */
    private NPlusOneReportResponse measure(Strategy strategy, String status, Pageable pageable) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        // Clear the cache so a previous strategy's cached data doesn't hide this one's N+1.
        entityManager.clear();
        long statementsBefore = statistics.getPrepareStatementCount();
        long collectionsBefore = statistics.getCollectionFetchCount();

        Outcome outcome = switch (strategy) {
            case NAIVE -> naive(status, pageable);
            case JOIN_FETCH -> joinFetch(status, pageable);
            case ENTITY_GRAPH -> entityGraph(status, pageable);
            case DTO_PROJECTION -> dtoProjection(status, pageable);
            case BATCH_FETCH -> batchFetch(status, pageable);
            case IDS_THEN_FETCH -> idsThenFetch(status, pageable);
        };

        long statements = statistics.getPrepareStatementCount() - statementsBefore;
        long collections = statistics.getCollectionFetchCount() - collectionsBefore;
        log.debug("Strategy {} produced {} rows in {} statements", strategy, outcome.orders().size(), statements);

        return NPlusOneReportResponse.builder()
                .strategy(strategy.name())
                .note(outcome.note())
                .rows(outcome.orders().size())
                .sqlStatements(statements)
                .collectionsFetched(collections)
                .statisticsEnabled(statistics.isStatisticsEnabled())
                .totalElements(outcome.totalElements())
                .orders(outcome.orders())
                .build();
    }

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#where-it-happens
     * The N+1 baseline: no fetch, so each row triggers its own extra select.
     */
    private Outcome naive(String status, Pageable pageable) {
        Page<Order> page = orderRepository.findByStatus(status, pageable);
        // Each getUser() call fires an extra select here - that's the N+1 in action.
        List<OrderSummaryResponse> orders = page.getContent().stream().map(this::toSummary).toList();
        return new Outcome(orders, page.getTotalElements(), "1 query for the page + 1 per row for Order.user");
    }

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#join-fetch
     * Fix #1: JOIN FETCH pulls the user into the same query.
     */
    private Outcome joinFetch(String status, Pageable pageable) {
        Page<Order> page = orderRepository.findByStatusJoinFetchUser(status, pageable);
        List<OrderSummaryResponse> orders = page.getContent().stream().map(this::toSummary).toList();
        return new Outcome(orders, page.getTotalElements(), "1 query, users already initialized");
    }

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#entity-graph
     * Fix #2: an {@code @EntityGraph} tells Hibernate to fetch the user without hand-writing JPQL.
     */
    private Outcome entityGraph(String status, Pageable pageable) {
        Page<Order> page = orderRepository.findWithUserByStatus(status, pageable);
        List<OrderSummaryResponse> orders = page.getContent().stream().map(this::toSummary).toList();
        return new Outcome(orders, page.getTotalElements(), "1 query, LEFT JOIN FETCH added by the graph");
    }

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#dto-projection
     * Fix #3: project straight into a DTO, so there are no entities to lazily reload.
     */
    private Outcome dtoProjection(String status, Pageable pageable) {
        Page<OrderSummaryResponse> page = orderRepository.findSummaryByStatus(status, pageable);
        return new Outcome(page.getContent(), page.getTotalElements(), "1 query, 6 columns, nothing managed");
    }

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#batch-fetching
     * Fix #4: {@code @BatchSize} groups the per-row collection loads into one batched query.
     */
    private Outcome batchFetch(String status, Pageable pageable) {
        Page<Order> page = orderRepository.findWithUserByStatus(status, pageable);
        // Batched thanks to @BatchSize(50): one select loads details for the whole page, not 20.
        List<OrderSummaryResponse> orders = page.getContent().stream().map(this::toSummaryWithItems).toList();
        return new Outcome(orders, page.getTotalElements(), "1 query + 1 batched select for all collections");
    }

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#ids-then-fetch
     * Fix #5: fetch just the ids first, then fetch the full data for those ids in one more query.
     */
    private Outcome idsThenFetch(String status, Pageable pageable) {
        Page<Long> idPage = orderRepository.findIdsByStatus(status, pageable);
        if (idPage.getContent().isEmpty()) {
            return new Outcome(List.of(), idPage.getTotalElements(), "1 query, empty page");
        }
        Map<Long, Order> byId = orderRepository.findWithUserAndDetailsByIdIn(idPage.getContent()).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        // Re-apply the original page order, since the second query has no ORDER BY of its own.
        List<OrderSummaryResponse> orders = idPage.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(this::toSummaryWithItems)
                .toList();
        return new Outcome(orders, idPage.getTotalElements(), "2 queries, collection fetched without in-memory paging");
    }

    private OrderSummaryResponse toSummary(Order order) {
        return OrderSummaryResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .userId(order.getUser() != null ? order.getUser().getId() : null)
                .userEmail(order.getUser() != null ? order.getUser().getEmail() : null)
                .build();
    }

    private OrderSummaryResponse toSummaryWithItems(Order order) {
        OrderSummaryResponse summary = toSummary(order);
        summary.setItemCount(order.getDetails().size());
        return summary;
    }

    private record Outcome(List<OrderSummaryResponse> orders, long totalElements, String note) {
    }
}
