package com.example.project2.controller.version;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.response.ProductResponse;
import com.example.project2.dto.response.v2.ProductV2Response;
import com.example.project2.mapper.ProductV2Mapper;
import com.example.project2.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interview topic: docs/interview/rest-api/01-rest-api-versioning.md#header-versioning
 *
 * <p>Header versioning: one URL, and the {@code version} attribute picks the method based on the
 * {@code X-API-Version} header. A request without that header falls back to v1, so older clients
 * keep working (set in application.properties).
 */
@RestController
@RequestMapping("/api/catalog/products")
@RequiredArgsConstructor
public class CatalogProductHeaderVersionController {

    private final ProductService productService;
    private final ProductV2Mapper productV2Mapper;

    @GetMapping(value = "/{id}", version = "1")
    public ApiResponse<ProductResponse> findByIdV1(@PathVariable Long id) {
        return ApiResponse.ok(productService.findById(id));
    }

    @GetMapping(value = "/{id}", version = "2")
    public ApiResponse<ProductV2Response> findByIdV2(@PathVariable Long id) {
        return ApiResponse.ok(productV2Mapper.toV2(productService.findById(id)));
    }
}
