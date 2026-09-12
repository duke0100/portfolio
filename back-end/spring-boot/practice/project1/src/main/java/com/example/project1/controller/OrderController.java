package com.example.project1.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project1.dto.request.CreateOrderRequest;
import com.example.project1.dto.request.UpdateOrderRequest;
import com.example.project1.dto.response.OrderResponse;
import com.example.project1.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Orders")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public ApiResponse<Page<OrderResponse>> findAll(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(orderService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(orderService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.create(request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @PatchMapping("/{id}")
    public ApiResponse<OrderResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderRequest request) {
        return ApiResponse.ok(orderService.update(id, request), "Order updated successfully");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return ApiResponse.ok(null, "Order deleted successfully");
    }
}
