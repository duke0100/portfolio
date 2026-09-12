package com.example.project2.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface CategoryUserRevenueProjection {

    String getCategoryName();

    Long getUserId();

    String getUsername();

    LocalDateTime getOrderMonth();

    Long getOrderCount();

    Long getTotalUnits();

    BigDecimal getTotalRevenue();

    BigDecimal getAvgProductPrice();
}
