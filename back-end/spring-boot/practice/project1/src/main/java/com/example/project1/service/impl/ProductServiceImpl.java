package com.example.project1.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project1.dto.request.CreateProductRequest;
import com.example.project1.dto.request.UpdateProductRequest;
import com.example.project1.dto.response.ProductResponse;
import com.example.project1.entity.Category;
import com.example.project1.entity.Product;
import com.example.project1.repository.CategoryRepository;
import com.example.project1.repository.ProductRepository;
import com.example.project1.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public Page<ProductResponse> findAll(Pageable pageable) {
        log.info("Fetching all products with pageable: {}", pageable);
        return productRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    public ProductResponse findById(Long id) {
        log.info("Fetching product by id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Product not found with id: " + id));
        return toResponse(product);
    }

    @Override
    public ProductResponse create(CreateProductRequest request) {
        log.info("Creating product with name: {}", request.getName());
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
                .status("ACTIVE")
                .brand(request.getBrand())
                .dimensions(request.getDimensions())
                .discountPercent(request.getDiscountPercent() != null ? request.getDiscountPercent() : BigDecimal.ZERO)
                .build();
        Product saved = productRepository.save(product);
        log.info("Product created with id: {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    public ProductResponse update(Long id, UpdateProductRequest request) {
        log.info("Updating product with id: {}", id);
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
        log.info("Product updated with id: {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    public void delete(Long id) {
        log.info("Deleting product with id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Product not found with id: " + id));
        productRepository.delete(product);
        log.info("Product deleted with id: {}", id);
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
