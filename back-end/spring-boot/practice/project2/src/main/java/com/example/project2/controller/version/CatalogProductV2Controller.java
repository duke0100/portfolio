package com.example.project2.controller.version;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.response.v2.ProductV2Response;
import com.example.project2.mapper.ProductV2Mapper;
import com.example.project2.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interview topic: docs/interview/rest-api/01-rest-api-versioning.md#uri-versioning
 *
 * <p>Same {@link ProductService} call as {@link CatalogProductV1Controller}, only the response
 * shape differs. One extra controller per version is the cost of URI versioning.
 */
@RestController
@RequestMapping("/api/v2/catalog/products")
@RequiredArgsConstructor
public class CatalogProductV2Controller {

    private final ProductService productService;
    private final ProductV2Mapper productV2Mapper;

    @GetMapping("/{id}")
    public ApiResponse<ProductV2Response> findById(@PathVariable Long id) {
        return ApiResponse.ok(productV2Mapper.toV2(productService.findById(id)));
    }
}
