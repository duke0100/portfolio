package com.example.project2.service.impl;

import com.example.project2.dto.request.CreateProductNoteRequest;
import com.example.project2.dto.request.ProductNoteFormRequest;
import com.example.project2.dto.response.ProductNoteResponse;
import com.example.project2.entity.Product;
import com.example.project2.repository.ProductRepository;
import com.example.project2.service.ProductNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Interview topic: docs/interview/rest-api/02-request-body-vs-model-attribute.md#one-service-two-inputs
 * Both methods do the same work, which is the point: the annotation only decides how the data got here.
 */
@Service
@RequiredArgsConstructor
public class ProductNoteServiceImpl implements ProductNoteService {

    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public ProductNoteResponse fromJson(CreateProductNoteRequest request) {
        return build("@RequestBody", "application/json body", request.getProductId(),
                request.getAuthor(), request.getMessage(), request.getRating(), request.getTags());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductNoteResponse fromForm(ProductNoteFormRequest request) {
        return build("@ModelAttribute", "form fields or query string", request.getProductId(),
                request.getAuthor(), request.getMessage(), request.getRating(),
                splitTags(request.getTags()));
    }

    /** Form data has no list type, so a comma-separated field is the usual workaround. */
    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
    }

    private ProductNoteResponse build(String boundBy, String readFrom, Long productId, String author,
                                      String message, Integer rating, List<String> tags) {
        String productName = productRepository.findById(productId)
                .map(Product::getName)
                .orElse(null);
        return ProductNoteResponse.builder()
                .boundBy(boundBy)
                .readFrom(readFrom)
                .productId(productId)
                .author(author)
                .message(message)
                .rating(rating)
                .tags(tags)
                .productName(productName)
                .build();
    }
}
