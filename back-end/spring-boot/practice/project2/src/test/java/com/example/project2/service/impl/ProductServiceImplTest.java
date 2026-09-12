package com.example.project2.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project2.dto.request.CreateProductRequest;
import com.example.project2.entity.Product;
import com.example.project2.repository.CategoryRepository;
import com.example.project2.repository.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Interview topic: docs/interview/spring-boot/02-test-slices-vs-mockito.md#mock-and-injectmocks
 * No Spring context at all - just plain Mockito mocks wired into the real
 * {@link ProductServiceImpl}. The fastest of the four test styles here.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @InjectMocks
    private ProductServiceImpl productService;

    @Test
    void findById_hit_incrementsHitCounter() {
        Product product = Product.builder().id(1L).name("Monitor").price(BigDecimal.TEN).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(meterRegistry.counter("product.lookup", "result", "hit")).thenReturn(counter);

        var response = productService.findById(1L);

        assertThat(response.getName()).isEqualTo("Monitor");
        verify(counter).increment();
        verify(meterRegistry, never()).counter("product.lookup", "result", "miss");
    }

    @Test
    void findById_miss_incrementsMissCounterAndThrowsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());
        when(meterRegistry.counter("product.lookup", "result", "miss")).thenReturn(counter);

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("99");
        verify(counter).increment();
    }

    @Test
    void create_duplicateSku_throwsConflict_withoutTouchingCategoryRepository() {
        CreateProductRequest request = CreateProductRequest.builder()
                .name("Webcam").price(BigDecimal.TEN).sku("WC-001").build();
        when(productRepository.existsBySku("WC-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SKU already exists");

        verify(categoryRepository, never()).findById(any());
        verify(productRepository, never()).save(any());
    }
}
