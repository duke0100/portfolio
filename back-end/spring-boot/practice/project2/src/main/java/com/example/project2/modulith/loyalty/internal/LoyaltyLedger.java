package com.example.project2.modulith.loyalty.internal;

import com.example.project2.modulith.loyalty.external.LoyaltyBalance;
import com.example.project2.modulith.loyalty.external.LoyaltyFacade;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#externalinternal-inside-a-module
 * (this class is the closed implementation behind LoyaltyFacade)
 *
 * <p>In-memory on purpose: the point of the slice is the module boundary, not the storage.
 */
@Service
class LoyaltyLedger implements LoyaltyFacade {

    private final Map<String, Integer> points = new ConcurrentHashMap<>();
    private final Map<String, List<String>> history = new ConcurrentHashMap<>();

    void award(String customerId, int earned, String reason) {
        points.merge(customerId, earned, Integer::sum);
        history.computeIfAbsent(customerId, key -> new CopyOnWriteArrayList<>()).add(reason);
    }

    @Override
    public LoyaltyBalance balanceOf(String customerId) {
        return new LoyaltyBalance(customerId,
                points.getOrDefault(customerId, 0),
                List.copyOf(history.getOrDefault(customerId, List.of())));
    }
}
