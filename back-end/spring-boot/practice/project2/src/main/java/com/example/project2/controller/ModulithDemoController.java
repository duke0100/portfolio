package com.example.project2.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.request.PlaceModulithOrderRequest;
import com.example.project2.modulith.core.TenantHolder;
import com.example.project2.modulith.loyalty.external.LoyaltyBalance;
import com.example.project2.modulith.loyalty.external.LoyaltyFacade;
import com.example.project2.modulith.ordering.external.OrderReceipt;
import com.example.project2.modulith.ordering.external.OrderingFacade;
import com.example.project2.modulith.ordering.external.PlaceOrderCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#main-module-aggregator
 * (this class is the aggregator code sample in that section)
 *
 * <p>The main module: it imports the modules' {@code external} packages and nothing else. Note that
 * only facades and records are imported here - no {@code internal} type is even visible.
 */
@RestController
@RequestMapping("/api/v1/modulith")
@RequiredArgsConstructor
public class ModulithDemoController {

    private final OrderingFacade orderingFacade;
    private final LoyaltyFacade loyaltyFacade;

    /** Sync path: HTTP -> ordering facade -> catalogue facade, all in one transaction. */
    @PostMapping("/orders")
    public ApiResponse<OrderReceipt> placeOrder(@Valid @RequestBody PlaceModulithOrderRequest request,
                                                @RequestHeader(name = "X-Tenant", defaultValue = "default") String tenant) {
        TenantHolder.set(tenant);
        try {
            return ApiResponse.created(orderingFacade.placeOrder(
                    new PlaceOrderCommand(request.getCustomerId(), request.getSku(), request.getQuantity())));
        } finally {
            TenantHolder.clear();
        }
    }

    /**
     * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#event-driven-async
     * (async path: read this a moment after POST /orders to see the listener's result)
     */
    @GetMapping("/loyalty/{customerId}")
    public ApiResponse<LoyaltyBalance> loyalty(@PathVariable String customerId) {
        return ApiResponse.ok(loyaltyFacade.balanceOf(customerId));
    }
}
