package com.example.project2.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.request.CreateProductRequest;
import com.example.project2.dto.request.UpdateProductRequest;
import com.example.project2.dto.response.ProductResponse;
import com.example.project2.service.ProductService;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * Interview topic: docs/interview/spring-boot/01-spring-boot-actuator.md#custom-metrics
     * Tracks its own timing metrics separately, since this endpoint pages over ~1M rows and we
     * want its performance visible on its own, not averaged in with every other request.
     */
    @Timed(value = "product.api",
            extraTags = {"operation", "findAll"},
            description = "Paged product listing",
            percentiles = {0.5, 0.95, 0.99})
    @GetMapping
    public ApiResponse<Page<ProductResponse>> findAll(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(productService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(productService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = productService.create(request);
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ProductResponse> update(
            @PathVariable Long id,
            @RequestBody UpdateProductRequest request) {
        return ApiResponse.ok(productService.update(id, request), "Product updated successfully");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.ok(null, "Product deleted successfully");
    }
}
