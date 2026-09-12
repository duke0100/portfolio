package com.example.project2.controller.version;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.response.ProductResponse;
import com.example.project2.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interview topic: docs/interview/rest-api/01-rest-api-versioning.md#uri-versioning
 *
 * <p>URI versioning: the version is part of the path, so you can tell which version a call uses
 * just by reading the URL.
 */
@RestController
@RequestMapping("/api/v1/catalog/products")
@RequiredArgsConstructor
public class CatalogProductV1Controller {

    private final ProductService productService;

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(productService.findById(id));
    }
}
