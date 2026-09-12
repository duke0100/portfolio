package com.example.project2.service;

import com.example.project2.dto.request.CreateCategoryRequest;
import com.example.project2.dto.response.CategoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CategoryService {

    Page<CategoryResponse> findAll(Pageable pageable);

    CategoryResponse findById(Long id);

    CategoryResponse create(CreateCategoryRequest request);
}
