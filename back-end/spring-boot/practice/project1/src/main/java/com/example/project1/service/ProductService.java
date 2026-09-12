package com.example.project1.service;

import com.example.project1.dto.request.CreateProductRequest;
import com.example.project1.dto.request.UpdateProductRequest;
import com.example.project1.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    Page<ProductResponse> findAll(Pageable pageable);

    ProductResponse findById(Long id);

    ProductResponse create(CreateProductRequest request);

    ProductResponse update(Long id, UpdateProductRequest request);

    void delete(Long id);
}
