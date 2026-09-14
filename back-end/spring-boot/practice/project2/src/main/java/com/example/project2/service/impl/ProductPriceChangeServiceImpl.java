package com.example.project2.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project2.dto.request.ChangeProductPriceRequest;
import com.example.project2.dto.response.ProductPriceChangeResponse;
import com.example.project2.entity.Product;
import com.example.project2.event.ProductPriceChanged;
import com.example.project2.repository.ProductRepository;
import com.example.project2.service.ProductPriceChangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#the-price-change
 * (this class is the publisher side of the after-commit audit sample)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductPriceChangeServiceImpl implements ProductPriceChangeService {

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional
    public ProductPriceChangeResponse changePrice(Long productId, ChangeProductPriceRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("Product " + productId + " was not found"));

        // The read above cannot decide the race, only the WHERE clause in this UPDATE can.
        int updated = productRepository.updatePriceIfUnchanged(
                productId, request.expectedPrice(), request.newPrice(), request.changedBy(), LocalDateTime.now());

        if (updated == 0) {
            throw BusinessException.conflict(
                    "Price of product %d is no longer %s - re-read it and try again"
                            .formatted(productId, request.expectedPrice()));
        }

        // Published inside the transaction, but the listener only runs if this commits.
        events.publishEvent(new ProductPriceChanged(
                productId, request.expectedPrice(), request.newPrice(), request.changedBy()));

        return new ProductPriceChangeResponse(
                productId,
                product.getName(),
                request.expectedPrice(),
                request.newPrice(),
                request.changedBy(),
                "SCHEDULED");
    }
}
