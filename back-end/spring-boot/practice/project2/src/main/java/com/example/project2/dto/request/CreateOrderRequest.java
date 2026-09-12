package com.example.project2.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    private String shippingAddress;

    private String billingAddress;

    private String paymentMethod;

    private String notes;

    private BigDecimal shippingFee;

    private BigDecimal discountAmount;

    private LocalDate estimatedDeliveryDate;

    @NotEmpty(message = "Order must have at least one item")
    private List<OrderItemRequest> items;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {

        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        private BigDecimal discount;

        private String notes;
    }
}
