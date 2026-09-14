package com.example.project2.service;

import com.example.project2.dto.request.ChangeProductPriceRequest;
import com.example.project2.dto.response.ProductPriceChangeResponse;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#the-price-change
 * Changes a product's price and announces it once the change is durable.
 */
public interface ProductPriceChangeService {

    /**
     * Applies the new price if the row still holds {@code expectedPrice}.
     *
     * @throws com.example.commonlib.exception.BusinessException 404 if the product is gone,
     *                                                          409 if someone else changed the price first
     */
    ProductPriceChangeResponse changePrice(Long productId, ChangeProductPriceRequest request);
}
