package com.example.project2.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project2.dto.request.CreateCategoryRequest;
import com.example.project2.dto.response.CategoryResponse;
import com.example.project2.entity.Category;
import com.example.project2.repository.CategoryRepository;
import com.example.project2.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public Page<CategoryResponse> findAll(Pageable pageable) {
        log.debug("Fetching all categories with pageable: {}", pageable);
        return categoryRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    public CategoryResponse findById(Long id) {
        log.debug("Fetching category by id: {}", id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Category not found with id: " + id));
        return toResponse(category);
    }

    @Override
    public CategoryResponse create(CreateCategoryRequest request) {
        log.debug("Creating category with name: {}", request.getName());
        if (categoryRepository.existsBySlug(request.getSlug())) {
            throw BusinessException.conflict("Slug already exists: " + request.getSlug());
        }
        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .parentId(request.getParentId())
                .imageUrl(request.getImageUrl())
                .slug(request.getSlug())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .isFeatured(request.getIsFeatured() != null ? request.getIsFeatured() : false)
                .metaTitle(request.getMetaTitle())
                .metaDescription(request.getMetaDescription())
                .build();
        Category saved = categoryRepository.save(category);
        log.info("Created category with id: {}", saved.getId());
        return toResponse(saved);
    }

    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .parentId(category.getParentId())
                .imageUrl(category.getImageUrl())
                .slug(category.getSlug())
                .status(category.getStatus())
                .displayOrder(category.getDisplayOrder())
                .level(category.getLevel())
                .isFeatured(category.getIsFeatured())
                .metaTitle(category.getMetaTitle())
                .metaDescription(category.getMetaDescription())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
