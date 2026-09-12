package com.example.project2.service;

import com.example.project2.dto.response.NPlusOneReportResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#measuring-it
 * (the six fetch strategies compared in that document, each behind one enum constant)
 */
public interface NPlusOneDemoService {

    enum Strategy {
        /** No fetch at all - one select per row for {@code Order.user}. */
        NAIVE,
        /** {@code join fetch} in JPQL plus a hand-written count query. */
        JOIN_FETCH,
        /** {@code @EntityGraph(attributePaths = "user")} on a derived query. */
        ENTITY_GRAPH,
        /** {@code select new OrderSummaryResponse(...)} - no entities, no proxies. */
        DTO_PROJECTION,
        /** Collection side: {@code @BatchSize(50)} on {@code Order.details}. */
        BATCH_FETCH,
        /** Paginate ids, then fetch the graph for those ids in a second query. */
        IDS_THEN_FETCH
    }

    NPlusOneReportResponse run(Strategy strategy, String status, Pageable pageable);

    List<NPlusOneReportResponse> compare(String status, Pageable pageable);
}
