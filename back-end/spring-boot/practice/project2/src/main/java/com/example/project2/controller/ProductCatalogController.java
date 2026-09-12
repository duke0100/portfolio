package com.example.project2.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.response.ProductScrollResponse;
import com.example.project2.dto.response.ProductSummaryResponse;
import com.example.project2.service.ProductCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.SortDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interview topic: docs/interview/rest-api/05-pagination-and-sorting.md#paged-endpoint
 * The paged and sorted product listing. It has its own path so it never clashes with
 * {@link ProductController} and its {@code /api/v1/products/{id}}.
 */
@RestController
@RequestMapping("/api/v1/catalog/products")
@RequiredArgsConstructor
public class ProductCatalogController {

    private final ProductCatalogService productCatalogService;

    /**
     * Interview topic: docs/interview/rest-api/05-pagination-and-sorting.md#defaults
     * Nothing here parses page, size or sort. Spring's PageableHandlerMethodArgumentResolver builds
     * the Pageable from the query string, and these two annotations say what to use when it is empty.
     *
     * <p>PagedModel wraps the Page so the JSON keeps a stable content plus page shape. Returning the
     * Page itself would publish Spring Data internals as if they were part of the API.
     */
    @GetMapping
    public ApiResponse<PagedModel<ProductSummaryResponse>> findPage(
            @PageableDefault(size = 20)
            @SortDefault(sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(new PagedModel<>(productCatalogService.findPage(pageable)));
    }

    /**
     * Interview topic: docs/interview/rest-api/05-pagination-and-sorting.md#slice-and-cursor-based-pagination
     * The endless-scroll variant. Send back the nextAfterId from the previous response, or leave it
     * out for the first batch. There is no total, so no count query over ~1M rows.
     */
    @GetMapping("/scroll")
    public ApiResponse<ProductScrollResponse> scroll(
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(productCatalogService.scroll(afterId, size));
    }
}
