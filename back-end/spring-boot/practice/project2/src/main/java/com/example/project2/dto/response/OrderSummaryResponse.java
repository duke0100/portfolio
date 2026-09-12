package com.example.project2.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#dto-projection
 * A flat, read-only view of an order plus its buyer - just the fields a listing screen needs,
 * instead of two full JPA entities.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryResponse {

    private Long id;
    private String orderNumber;
    private String status;
    private BigDecimal totalAmount;
    private Long userId;
    private String userEmail;

    /** Only populated by the strategies that touch {@code Order.details}; null otherwise. */
    private Integer itemCount;

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#dto-projection
     * Matches the JPQL {@code select new ...} expression in the repository - if the parameter
     * order or types drift apart, it fails at runtime, not at compile time.
     */
    public OrderSummaryResponse(Long id, String orderNumber, String status, BigDecimal totalAmount,
                                Long userId, String userEmail) {
        this(id, orderNumber, status, totalAmount, userId, userEmail, null);
    }
}
