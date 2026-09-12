package com.example.project2.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.request.CreateOrderRequest;
import com.example.project2.dto.request.ReplaceShippingAddressRequest;
import com.example.project2.dto.response.OrderResponse;
import com.example.project2.service.IdempotencyService;
import com.example.project2.service.IdempotencyService.IdempotentRequest;
import com.example.project2.service.IdempotencyService.IdempotentResult;
import com.example.project2.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#the-post-endpoint
 * Checkout written so a client can safely retry every call in it.
 *
 * <p>POST needs an Idempotency-Key to be retry-safe; the PUT and the DELETE below are idempotent
 * by their own semantics and need nothing extra.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
public class IdempotentCheckoutController {

    static final String ORDERS_PATH = "/api/v1/checkout/orders";

    /** Tells the client the order already existed, so it can skip a "created" notification. */
    static final String REPLAYED_HEADER = "Idempotency-Replayed";

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;

    /**
     * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#the-post-endpoint
     * The header is required: without it a timed-out retry would place a second order, and we would
     * rather fail the call than take that risk.
     */
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @RequestHeader("Idempotency-Key")
            @NotBlank(message = "Idempotency-Key must not be blank")
            @Size(max = 120, message = "Idempotency-Key must be at most 120 characters")
            String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        IdempotentResult<OrderResponse> result = idempotencyService.execute(
                new IdempotentRequest(idempotencyKey, "POST " + ORDERS_PATH, request,
                        HttpStatus.CREATED.value()),
                OrderResponse.class,
                () -> orderService.create(request));

        return ResponseEntity.status(result.status())
                .header(REPLAYED_HEADER, Boolean.toString(result.replayed()))
                .body(ApiResponse.created(result.body()));
    }

    /**
     * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#put-and-delete
     * PUT carries the full new address, so two identical calls leave the same row. PATCH would not
     * be safe here if the body said "append to the address" instead of "set it to this".
     */
    @PutMapping("/orders/{id}/addresses")
    public ApiResponse<OrderResponse> replaceAddresses(
            @PathVariable Long id,
            @Valid @RequestBody ReplaceShippingAddressRequest request) {
        return ApiResponse.ok(orderService.replaceAddresses(id, request), "Addresses replaced");
    }

    /**
     * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#put-and-delete
     * 204 whether or not the order was still there. The effect the caller asked for - "this order
     * is gone" - holds either way, so a retry after a lost response is not an error.
     */
    @DeleteMapping("/orders/{id}")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long id) {
        boolean deleted = orderService.deleteIfExists(id);
        log.info("Cancel order {} - deleted now: {}", id, deleted);
        return ResponseEntity.noContent().build();
    }
}
