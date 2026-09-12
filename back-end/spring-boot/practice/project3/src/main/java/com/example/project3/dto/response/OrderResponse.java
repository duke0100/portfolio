package com.example.project3.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private UUID orderId;

    private UUID userId;

    private String userEmail;

    private String orderNumber;

    private String status;

    private BigDecimal totalAmount;

    private String shippingAddress;

    private String billingAddress;

    private String paymentMethod;

    private String paymentStatus;

    private String notes;

    private BigDecimal shippingFee;

    private BigDecimal discountAmount;

    private LocalDate estimatedDeliveryDate;

    private LocalDate actualDeliveryDate;

    private Instant createdAt;

    private Instant updatedAt;
}
