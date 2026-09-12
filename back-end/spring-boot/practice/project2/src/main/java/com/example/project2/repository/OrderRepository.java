package com.example.project2.repository;

import com.example.project2.dto.response.OrderSummaryResponse;
import com.example.project2.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#where-it-happens
     * Left un-fetched on purpose - this is the "before" version used to demonstrate the N+1 problem.
     * Don't "fix" it.
     */
    Page<Order> findByStatus(String status, Pageable pageable);

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#join-fetch
     * The explicit {@code countQuery} is required here - Hibernate can't derive a count query
     * from a {@code join fetch} on its own.
     */
    @Query(value = """
            select o from Order o
            join fetch o.user
            where o.status = :status
            """,
            countQuery = "select count(o) from Order o where o.status = :status")
    Page<Order> findByStatusJoinFetchUser(@Param("status") String status, Pageable pageable);

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#entity-graph
     * "WithUser" in the method name is just documentation for humans - the {@code @EntityGraph}
     * is what actually tells Hibernate to fetch the user in the same query.
     */
    @EntityGraph(attributePaths = "user")
    Page<Order> findWithUserByStatus(String status, Pageable pageable);

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#dto-projection
     * Returns plain DTOs instead of entities, so there's nothing left to trigger a later lazy load.
     * The constructor args must match {@link OrderSummaryResponse}'s constructor exactly.
     */
    @Query(value = """
            select new com.example.project2.dto.response.OrderSummaryResponse(
                o.id, o.orderNumber, o.status, o.totalAmount, u.id, u.email)
            from Order o
            join o.user u
            where o.status = :status
            """,
            countQuery = "select count(o) from Order o where o.status = :status")
    Page<OrderSummaryResponse> findSummaryByStatus(@Param("status") String status, Pageable pageable);

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#ids-then-fetch
     * Step 1 of the two-query pattern: page the ids first, with no joins.
     */
    @Query(value = "select o.id from Order o where o.status = :status",
            countQuery = "select count(o) from Order o where o.status = :status")
    Page<Long> findIdsByStatus(@Param("status") String status, Pageable pageable);

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#ids-then-fetch
     * Step 2: fetch the full order graph, including collections, for just those ids - safe here
     * since there's no pagination left to be thrown off by extra rows.
     */
    @EntityGraph(attributePaths = {"user", "details"})
    List<Order> findWithUserAndDetailsByIdIn(List<Long> ids);
}
