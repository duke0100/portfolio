package com.example.project2.service;

import com.example.project2.dto.request.CreateOrderRequest;
import com.example.project2.dto.request.ReplaceShippingAddressRequest;
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

    /**
     * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#put-and-delete
     * Replaces both addresses with exactly what the caller sent, so sending it twice leaves the
     * same state.
     */
    OrderResponse replaceAddresses(Long id, ReplaceShippingAddressRequest request);

    void delete(Long id);

    /**
     * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#put-and-delete
     * Deletes the order if it is still there and reports whether it was.
     *
     * <p>Unlike {@link #delete(Long)} this does not throw when the order is gone, which is what lets
     * the endpoint answer 204 to a retried DELETE.
     */
    boolean deleteIfExists(Long id);

    Page<OrderDetailReportResponse> findOrderDetailReport(String status, LocalDateTime since, Pageable pageable);

    Page<CategoryUserRevenueResponse> findCategoryUserRevenueReport(Pageable pageable);
}
