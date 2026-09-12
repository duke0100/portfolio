package com.example.project2.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface OrderDetailReportProjection {

    Long getOrderId();

    String getOrderNumber();

    String getOrderStatus();

    String getPaymentStatus();

    BigDecimal getTotalAmount();

    LocalDateTime getOrderCreatedAt();

    Long getUserId();

    String getUsername();

    String getEmail();

    String getFirstName();

    String getLastName();

    Long getOrderDetailId();

    Integer getQuantity();

    BigDecimal getUnitPrice();

    BigDecimal getItemDiscount();

    BigDecimal getTotalPrice();

    Long getProductId();

    String getProductName();

    String getProductSku();

    BigDecimal getCurrentPrice();

    Integer getStockQuantity();

    Long getCategoryId();

    String getCategoryName();

    String getCategorySlug();
}
