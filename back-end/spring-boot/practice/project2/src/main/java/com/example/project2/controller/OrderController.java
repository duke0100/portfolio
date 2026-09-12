package com.example.project2.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.request.CreateOrderRequest;
import com.example.project2.dto.request.UpdateOrderRequest;
import com.example.project2.dto.response.CategoryUserRevenueResponse;
import com.example.project2.dto.response.OrderDetailReportResponse;
import com.example.project2.dto.response.OrderResponse;
import com.example.project2.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

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
            @RequestBody UpdateOrderRequest request) {
        return ApiResponse.ok(orderService.update(id, request), "Order updated successfully");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return ApiResponse.ok(null, "Order deleted successfully");
    }

    // Joins order_details, orders, users, products and categories (all 5 tables)
    // for performance testing at scale.
    @GetMapping("/reports/details")
    public ApiResponse<Page<OrderDetailReportResponse>> orderDetailReport(
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(orderService.findOrderDetailReport(status, since, pageable));
    }

    // Same 5-table join, aggregated into monthly revenue per category/user.
    @GetMapping("/reports/revenue-by-category-user")
    public ApiResponse<Page<CategoryUserRevenueResponse>> categoryUserRevenueReport(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(orderService.findCategoryUserRevenueReport(pageable));
    }
}
