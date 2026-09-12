package com.example.project1.service;

import com.example.project1.dto.request.CreateOrderRequest;
import com.example.project1.dto.request.UpdateOrderRequest;
import com.example.project1.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    Page<OrderResponse> findAll(Pageable pageable);

    OrderResponse findById(Long id);

    OrderResponse create(CreateOrderRequest request);

    OrderResponse update(Long id, UpdateOrderRequest request);

    void delete(Long id);
}
