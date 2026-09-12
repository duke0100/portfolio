package com.example.project3.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project3.dto.request.CreateProductRequest;
import com.example.project3.dto.request.UpdateProductRequest;
import com.example.project3.dto.response.ProductResponse;
import com.example.project3.entity.Product;
import com.example.project3.repository.ProductRepository;
import com.example.project3.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    public List<ProductResponse> findAll() {
        log.info("Fetching all products");
        return productRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ProductResponse findById(UUID id) {
        log.info("Fetching product with id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Product not found with id: " + id));
        return toResponse(product);
    }

    @Override
    public ProductResponse create(CreateProductRequest request) {
        log.info("Creating product with name: {}", request.getName());

        Product product = Product.builder()
                .productId(UUID.randomUUID())
                .categoryId(request.getCategoryId())
                .categoryName(request.getCategoryName())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .sku(request.getSku())
                .imageUrl(request.getImageUrl())
                .weight(request.getWeight())
                .brand(request.getBrand())
                .dimensions(request.getDimensions())
                .discountPercent(request.getDiscountPercent())
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();

        Product saved = productRepository.save(product);
        log.info("Product created with id: {}", saved.getProductId());
        return toResponse(saved);
    }

    @Override
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        log.info("Updating product with id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Product not found with id: " + id));

        if (request.getCategoryId() != null) {
            product.setCategoryId(request.getCategoryId());
        }
        if (request.getCategoryName() != null) {
            product.setCategoryName(request.getCategoryName());
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

        product.setUpdatedAt(Instant.now());

        Product saved = productRepository.save(product);
        log.info("Product updated with id: {}", saved.getProductId());
        return toResponse(saved);
    }

    @Override
    public void delete(UUID id) {
        log.info("Deleting product with id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Product not found with id: " + id));
        productRepository.delete(product);
        log.info("Product deleted with id: {}", id);
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .productId(product.getProductId())
                .categoryId(product.getCategoryId())
                .categoryName(product.getCategoryName())
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
