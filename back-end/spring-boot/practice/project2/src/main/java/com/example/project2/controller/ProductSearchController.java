package com.example.project2.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.request.ProductSearchRequest;
import com.example.project2.dto.response.ProductSearchReportResponse;
import com.example.project2.service.ProductSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Interview topic: docs/interview/spring-data-jpa/02-jpql-criteria-native-queries.md#running-all-three
 * The same product search in three query styles. It sits on {@code /api/v1/product-search} so it
 * never clashes with {@code ProductController} and its {@code /api/v1/products/{id}}.
 */
@RestController
@RequestMapping("/api/v1/product-search")
@RequiredArgsConstructor
public class ProductSearchController {

    private final ProductSearchService productSearchService;

    /** One style at a time, e.g. {@code ?style=CRITERIA&brand=Acme&minPrice=10&inStock=true}. */
    @GetMapping
    public ApiResponse<ProductSearchReportResponse> search(
            @RequestParam(defaultValue = "CRITERIA") ProductSearchService.QueryStyle style,
            @Valid @ModelAttribute ProductSearchRequest request,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ApiResponse.ok(productSearchService.search(style, request, pageable));
    }

    /** All styles on the same filters, so you can compare their SQL counts. */
    @GetMapping("/compare")
    public ApiResponse<List<ProductSearchReportResponse>> compare(
            @Valid @ModelAttribute ProductSearchRequest request,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ApiResponse.ok(productSearchService.compare(request, pageable));
    }
}
