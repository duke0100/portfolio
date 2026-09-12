package com.example.project2.modulith.loyalty.external;

import java.util.List;

/** What the loyalty module lets the outside read. */
public record LoyaltyBalance(String customerId, int points, List<String> history) {
}
