package com.example.project2.controller.version;

import com.example.project2.config.WebConfig;
import com.example.project2.dto.response.ProductResponse;
import com.example.project2.mapper.ProductV2Mapper;
import com.example.project2.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Interview topic: docs/interview/rest-api/01-rest-api-versioning.md#testing-the-routing
 *
 * <p>These check routing, not business logic: the same mocked {@link ProductService} answer has
 * to come back in the v1 or v2 shape depending on the URL, header or Accept type.
 */
@WebMvcTest({CatalogProductV1Controller.class, CatalogProductV2Controller.class,
        CatalogProductHeaderVersionController.class, CatalogProductMediaTypeController.class})
@Import({ProductV2Mapper.class, WebConfig.class})
class ApiVersioningWebMvcTest {

    private static final ProductResponse PRODUCT = ProductResponse.builder()
            .id(1L)
            .categoryId(7L)
            .categoryName("Keyboards")
            .name("Mechanical Keyboard")
            .sku("KB-001")
            .price(new BigDecimal("100.00"))
            .discountPercent(new BigDecimal("10.00"))
            .dimensions("30x20x5")
            .status("ACTIVE")
            .build();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void uriVersioning_v1_returnsFlatPrice() throws Exception {
        when(productService.findById(1L)).thenReturn(PRODUCT);

        mockMvc.perform(get("/api/v1/catalog/products/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(100.00))
                .andExpect(jsonPath("$.data.categoryName").value("Keyboards"))
                .andExpect(jsonPath("$.data.pricing").doesNotExist());
    }

    @Test
    void uriVersioning_v2_returnsNestedPricingAndSplitDimensions() throws Exception {
        when(productService.findById(1L)).thenReturn(PRODUCT);

        mockMvc.perform(get("/api/v2/catalog/products/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").doesNotExist())
                .andExpect(jsonPath("$.data.pricing.listPrice").value(100.00))
                .andExpect(jsonPath("$.data.pricing.effectivePrice").value(90.00))
                .andExpect(jsonPath("$.data.pricing.currency").value("USD"))
                .andExpect(jsonPath("$.data.category.name").value("Keyboards"))
                .andExpect(jsonPath("$.data.dimensions.lengthCm").value(30))
                .andExpect(jsonPath("$.data.dimensions.heightCm").value(5));
    }

    @Test
    void headerVersioning_routesOnXApiVersion() throws Exception {
        when(productService.findById(1L)).thenReturn(PRODUCT);

        mockMvc.perform(get("/api/catalog/products/{id}", 1L).header("X-API-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(100.00));

        mockMvc.perform(get("/api/catalog/products/{id}", 1L).header("X-API-Version", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pricing.effectivePrice").value(90.00));
    }

    @Test
    void headerVersioning_noHeader_fallsBackToTheDefaultVersion() throws Exception {
        when(productService.findById(1L)).thenReturn(PRODUCT);

        mockMvc.perform(get("/api/catalog/products/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(100.00));
    }

    @Test
    void headerVersioning_unsupportedVersion_isRejected() throws Exception {
        mockMvc.perform(get("/api/catalog/products/{id}", 1L).header("X-API-Version", "9"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mediaTypeVersioning_routesOnAcceptHeader() throws Exception {
        when(productService.findAll(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of(PRODUCT), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/catalog/products").accept(CatalogProductMediaTypeController.V1_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(CatalogProductMediaTypeController.V1_JSON))
                .andExpect(jsonPath("$.data.content[0].price").value(100.00));

        mockMvc.perform(get("/api/catalog/products").accept(CatalogProductMediaTypeController.V2_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(CatalogProductMediaTypeController.V2_JSON))
                .andExpect(jsonPath("$.data.content[0].pricing.effectivePrice").value(90.00));
    }

    @Test
    void mediaTypeVersioning_wildcardAccept_fallsBackToV1AsPlainJson() throws Exception {
        when(productService.findAll(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of(PRODUCT), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.data.content[0].price").value(100.00));
    }
}
