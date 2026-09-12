package com.example.project2.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailReportResponse {

    private Long orderId;
    private String orderNumber;
    private String orderStatus;
    private String paymentStatus;
    private BigDecimal totalAmount;
    private LocalDateTime orderCreatedAt;

    private Long userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;

    private Long orderDetailId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal itemDiscount;
    private BigDecimal totalPrice;

    private Long productId;
    private String productName;
    private String productSku;
    private BigDecimal currentPrice;
    private Integer stockQuantity;

    private Long categoryId;
    private String categoryName;
    private String categorySlug;
}
