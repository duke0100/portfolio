package com.example.project2.service;

import com.example.project2.dto.request.ReserveStockRequest;
import com.example.project2.dto.response.StockReservationResponse;

/**
 * Interview topic: docs/interview/rest-api/03-global-exception-handling.md#domain-exceptions
 * (the service that throws the exceptions the advice translates)
 */
public interface StockReservationService {

    StockReservationResponse reserve(ReserveStockRequest request);

    int currentStock(Long productId);
}
