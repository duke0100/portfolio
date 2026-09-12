package com.example.project2.controller;

import com.example.commonlib.exception.BusinessException;
import com.example.project2.dto.request.CreateProductRequest;
import com.example.project2.dto.response.ProductResponse;
import com.example.project2.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Interview topic: docs/interview/spring-boot/02-test-slices-vs-mockito.md#webmvctest
 * Only starts the web layer - {@link ProductController} and Spring MVC. {@link ProductService}
 * is a mock here, so the real service, repositories and database are never touched.
 */
@WebMvcTest(ProductController.class)
class ProductControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @Test
    void findAll_returnsPagedProducts() throws Exception {
        ProductResponse product = ProductResponse.builder()
                .id(1L).name("Keyboard").price(BigDecimal.valueOf(49.90)).build();
        when(productService.findAll(any())).thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Keyboard"));
    }

    @Test
    void findById_notFound_isTranslatedByGlobalExceptionHandler() throws Exception {
        // @WebMvcTest picks up common-lib's GlobalExceptionHandler automatically, no @Import needed.
        when(productService.findById(99L)).thenThrow(BusinessException.notFound("Product not found with id: 99"));

        mockMvc.perform(get("/api/v1/products/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void create_blankName_failsBeanValidation_beforeServiceIsCalled() throws Exception {
        CreateProductRequest invalid = CreateProductRequest.builder()
                .name("")
                .price(BigDecimal.TEN)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verify(productService, org.mockito.Mockito.never()).create(any());
    }
}
