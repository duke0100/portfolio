package com.example.project3.service;

import com.example.project3.dto.request.CreateOrderRequest;
import com.example.project3.dto.request.UpdateOrderRequest;
import com.example.project3.dto.response.OrderResponse;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    List<OrderResponse> findAll();

    OrderResponse findById(UUID id);

    OrderResponse create(CreateOrderRequest request);

    OrderResponse update(UUID id, UpdateOrderRequest request);

    void delete(UUID id);
}
