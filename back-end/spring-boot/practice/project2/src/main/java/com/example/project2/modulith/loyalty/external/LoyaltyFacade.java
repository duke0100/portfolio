package com.example.project2.modulith.loyalty.external;

/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#facade-sync
 * (this interface is the read-side facade sample in that section)
 */
public interface LoyaltyFacade {

    LoyaltyBalance balanceOf(String customerId);
}
