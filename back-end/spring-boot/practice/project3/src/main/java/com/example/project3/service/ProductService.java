package com.example.project3.service;

import com.example.project3.dto.request.CreateProductRequest;
import com.example.project3.dto.request.UpdateProductRequest;
import com.example.project3.dto.response.ProductResponse;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    List<ProductResponse> findAll();

    ProductResponse findById(UUID id);

    ProductResponse create(CreateProductRequest request);

    ProductResponse update(UUID id, UpdateProductRequest request);

    void delete(UUID id);
}
