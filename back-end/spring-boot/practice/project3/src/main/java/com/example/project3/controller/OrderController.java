package com.example.project3.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project3.dto.request.CreateOrderRequest;
import com.example.project3.dto.request.UpdateOrderRequest;
import com.example.project3.dto.response.OrderResponse;
import com.example.project3.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public ApiResponse<List<OrderResponse>> findAll() {
        return ApiResponse.ok(orderService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> findById(@PathVariable UUID id) {
        return ApiResponse.ok(orderService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.create(request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @PatchMapping("/{id}")
    public ApiResponse<OrderResponse> update(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateOrderRequest request) {
        return ApiResponse.ok(orderService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        orderService.delete(id);
        return ApiResponse.ok(null, "Order deleted successfully");
    }
}
