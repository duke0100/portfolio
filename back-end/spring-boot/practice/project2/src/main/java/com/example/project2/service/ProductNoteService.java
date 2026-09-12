package com.example.project2.service;

import com.example.project2.dto.request.CreateProductNoteRequest;
import com.example.project2.dto.request.ProductNoteFormRequest;
import com.example.project2.dto.response.ProductNoteResponse;

/**
 * Interview topic: docs/interview/rest-api/02-request-body-vs-model-attribute.md#one-service-two-inputs
 * Two entry points because the two DTOs differ, one shared result because the business rule does not.
 */
public interface ProductNoteService {

    /** Handles the note that arrived as a JSON body. */
    ProductNoteResponse fromJson(CreateProductNoteRequest request);

    /** Handles the note that arrived as form fields or query parameters. */
    ProductNoteResponse fromForm(ProductNoteFormRequest request);
}
