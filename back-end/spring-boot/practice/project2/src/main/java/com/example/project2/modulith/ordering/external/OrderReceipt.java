package com.example.project2.modulith.ordering.external;

import java.math.BigDecimal;

/** Output of the ordering facade. */
public record OrderReceipt(String orderId,
                           String customerId,
                           String sku,
                           String productName,
                           int quantity,
                           BigDecimal total) {
}
