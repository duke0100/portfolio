package com.example.project2.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project2.dto.request.CreateProductRequest;
import com.example.project2.dto.request.UpdateProductRequest;
import com.example.project2.dto.response.ProductResponse;
import com.example.project2.entity.Category;
import com.example.project2.entity.Product;
import com.example.project2.repository.CategoryRepository;
import com.example.project2.repository.ProductRepository;
import com.example.project2.service.ProductService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#open-session-in-view
 * Class is {@code @Transactional} because open-session-in-view is turned off, so loading a
 * product's category now needs a real transaction instead of relying on one staying open for the request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MeterRegistry meterRegistry;

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#open-session-in-view
     * Needs its own read-only transaction, or mapping the lazy category to a DTO throws
     * {@code LazyInitializationException} now that open-session-in-view is off.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(Pageable pageable) {
        log.debug("Fetching all products with pageable: {}", pageable);
        return productRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Interview topic: docs/interview/spring-boot/01-spring-boot-actuator.md#custom-metrics
     * Tracks product lookup hits vs. misses as its own metric, since that's a number a product
     * owner would actually care about, separate from generic HTTP 404 counts.
     */
    @Override
    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        log.debug("Fetching product by id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> {
                    countLookup("miss");
                    return BusinessException.notFound("Product not found with id: " + id);
                });
        countLookup("hit");
        return toResponse(product);
    }

    private void countLookup(String result) {
        meterRegistry.counter("product.lookup", "result", result).increment();
    }

    @Override
    public ProductResponse create(CreateProductRequest request) {
        log.debug("Creating product with name: {}", request.getName());
        if (request.getSku() != null && productRepository.existsBySku(request.getSku())) {
            throw BusinessException.conflict("SKU already exists: " + request.getSku());
        }
        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> BusinessException.notFound("Category not found with id: " + request.getCategoryId()));
        }
        Product product = Product.builder()
                .category(category)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 0)
                .sku(request.getSku())
                .imageUrl(request.getImageUrl())
                .weight(request.getWeight())
                .brand(request.getBrand())
                .dimensions(request.getDimensions())
                .discountPercent(request.getDiscountPercent())
                .build();
        Product saved = productRepository.save(product);
        log.info("Created product with id: {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    public ProductResponse update(Long id, UpdateProductRequest request) {
        log.debug("Updating product with id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Product not found with id: " + id));
        if (request.getSku() != null && productRepository.existsBySkuAndIdNot(request.getSku(), id)) {
            throw BusinessException.conflict("SKU already exists: " + request.getSku());
        }
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> BusinessException.notFound("Category not found with id: " + request.getCategoryId()));
            product.setCategory(category);
        }
        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getStockQuantity() != null) {
            product.setStockQuantity(request.getStockQuantity());
        }
        if (request.getSku() != null) {
            product.setSku(request.getSku());
        }
        if (request.getImageUrl() != null) {
            product.setImageUrl(request.getImageUrl());
        }
        if (request.getWeight() != null) {
            product.setWeight(request.getWeight());
        }
        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }
        if (request.getBrand() != null) {
            product.setBrand(request.getBrand());
        }
        if (request.getDimensions() != null) {
            product.setDimensions(request.getDimensions());
        }
        if (request.getDiscountPercent() != null) {
            product.setDiscountPercent(request.getDiscountPercent());
        }
        Product saved = productRepository.save(product);
        log.info("Updated product with id: {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    public void delete(Long id) {
        log.debug("Deleting product with id: {}", id);
        if (!productRepository.existsById(id)) {
            throw BusinessException.notFound("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
        log.info("Deleted product with id: {}", id);
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .sku(product.getSku())
                .imageUrl(product.getImageUrl())
                .weight(product.getWeight())
                .status(product.getStatus())
                .brand(product.getBrand())
                .dimensions(product.getDimensions())
                .discountPercent(product.getDiscountPercent())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
