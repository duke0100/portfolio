package com.example.project2.controller.version;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.response.ProductResponse;
import com.example.project2.dto.response.v2.ProductV2Response;
import com.example.project2.mapper.ProductV2Mapper;
import com.example.project2.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interview topic: docs/interview/rest-api/01-rest-api-versioning.md#media-type-versioning
 *
 * <p>Media type versioning: the client picks a version through the {@code Accept} header and
 * content negotiation routes the call. v1 also answers plain {@code application/json} so a caller
 * with no specific Accept header still gets a usable response.
 */
@RestController
@RequestMapping("/api/catalog/products")
@RequiredArgsConstructor
public class CatalogProductMediaTypeController {

    public static final String V1_JSON = "application/vnd.catalog.v1+json";
    public static final String V2_JSON = "application/vnd.catalog.v2+json";

    private final ProductService productService;
    private final ProductV2Mapper productV2Mapper;

    @GetMapping(produces = {V1_JSON, MediaType.APPLICATION_JSON_VALUE})
    public ApiResponse<Page<ProductResponse>> findAllV1(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(productService.findAll(pageable));
    }

    @GetMapping(produces = V2_JSON)
    public ApiResponse<Page<ProductV2Response>> findAllV2(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(productService.findAll(pageable).map(productV2Mapper::toV2));
    }
}
