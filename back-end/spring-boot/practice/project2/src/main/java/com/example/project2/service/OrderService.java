package com.example.project2.service;

import com.example.project2.dto.request.CreateOrderRequest;
import com.example.project2.dto.request.UpdateOrderRequest;
import com.example.project2.dto.response.CategoryUserRevenueResponse;
import com.example.project2.dto.response.OrderDetailReportResponse;
import com.example.project2.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface OrderService {

    Page<OrderResponse> findAll(Pageable pageable);

    OrderResponse findById(Long id);

    OrderResponse create(CreateOrderRequest request);

    OrderResponse update(Long id, UpdateOrderRequest request);

    void delete(Long id);

    Page<OrderDetailReportResponse> findOrderDetailReport(String status, LocalDateTime since, Pageable pageable);

    Page<CategoryUserRevenueResponse> findCategoryUserRevenueReport(Pageable pageable);
}
