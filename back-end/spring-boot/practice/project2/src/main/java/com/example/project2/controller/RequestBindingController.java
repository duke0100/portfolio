package com.example.project2.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.request.CreateProductNoteRequest;
import com.example.project2.dto.request.ProductNoteFormRequest;
import com.example.project2.dto.response.ProductNoteResponse;
import com.example.project2.service.ProductNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * Interview topic: docs/interview/rest-api/02-request-body-vs-model-attribute.md#answer
 * The same product note is accepted three ways so the two annotations can be compared side by side.
 */
@RestController
@RequestMapping("/api/v1/binding/product-notes")
@RequiredArgsConstructor
public class RequestBindingController {

    private final ProductNoteService productNoteService;

    /**
     * Interview topic: docs/interview/rest-api/02-request-body-vs-model-attribute.md#requestbody-json
     * (the JSON code sample in that section)
     *
     * <p>Spring hands the raw body to Jackson, which turns it into the request object.
     */
    @PostMapping(path = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ProductNoteResponse> fromJson(@Valid @RequestBody CreateProductNoteRequest request) {
        return ApiResponse.ok(productNoteService.fromJson(request));
    }

    /**
     * Interview topic: docs/interview/rest-api/02-request-body-vs-model-attribute.md#modelattribute-form-and-query
     * (the HTML form code sample in that section)
     *
     * <p>Spring reads the posted form fields and matches each one to a setter by name.
     */
    @PostMapping(path = "/form", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ApiResponse<ProductNoteResponse> fromForm(@Valid @ModelAttribute ProductNoteFormRequest request) {
        return ApiResponse.ok(productNoteService.fromForm(request));
    }

    /**
     * Interview topic: docs/interview/rest-api/02-request-body-vs-model-attribute.md#modelattribute-form-and-query
     * (the query-string code sample in that section)
     *
     * <p>Identical binding with no body at all, which is why a GET can use @ModelAttribute but never @RequestBody.
     */
    @GetMapping("/query")
    public ApiResponse<ProductNoteResponse> fromQuery(@Valid @ModelAttribute ProductNoteFormRequest request) {
        return ApiResponse.ok(productNoteService.fromForm(request));
    }
}
