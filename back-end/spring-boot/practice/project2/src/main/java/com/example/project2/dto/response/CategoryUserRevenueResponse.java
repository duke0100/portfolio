package com.example.project2.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryUserRevenueResponse {

    private String categoryName;
    private Long userId;
    private String username;
    private LocalDateTime orderMonth;
    private Long orderCount;
    private Long totalUnits;
    private BigDecimal totalRevenue;
    private BigDecimal avgProductPrice;
}
