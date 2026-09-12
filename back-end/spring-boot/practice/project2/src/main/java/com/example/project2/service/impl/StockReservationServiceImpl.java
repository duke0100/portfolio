package com.example.project2.service.impl;

import com.example.project2.dto.request.ReserveStockRequest;
import com.example.project2.dto.response.StockReservationResponse;
import com.example.project2.entity.Product;
import com.example.project2.exception.ProductNotFoundException;
import com.example.project2.exception.StockUnavailableException;
import com.example.project2.repository.ProductRepository;
import com.example.project2.service.StockReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interview topic: docs/interview/rest-api/03-global-exception-handling.md#domain-exceptions
 * (this class is the "throw, don't catch" sample in that section)
 *
 * <p>The service knows nothing about HTTP: it throws domain exceptions and the advice maps them
 * to status codes.
 */
@Service
@RequiredArgsConstructor
public class StockReservationServiceImpl implements StockReservationService {

    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public StockReservationResponse reserve(ReserveStockRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(request.getProductId()));

        int available = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
        if (available < request.getQuantity()) {
            throw new StockUnavailableException(product.getId(), request.getQuantity(), available);
        }

        // Read-only on purpose: this endpoint exists to show error handling, not to move stock.
        return StockReservationResponse.builder()
                .productId(product.getId())
                .sku(product.getSku())
                .requested(request.getQuantity())
                .remaining(available - request.getQuantity())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public int currentStock(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return product.getStockQuantity() == null ? 0 : product.getStockQuantity();
    }
}
