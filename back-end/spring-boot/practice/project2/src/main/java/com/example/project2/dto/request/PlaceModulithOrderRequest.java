package com.example.project2.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Interview topic: docs/interview/architecture/01-spring-modulith-modular-monolith.md#main-module-aggregator
 * (this DTO is the web-edge input in that section's controller sample)
 *
 * <p>Web-layer DTO. The controller maps it to the ordering module's own command record, so HTTP
 * concerns like validation never leak into the module.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceModulithOrderRequest {

    @NotBlank(message = "Customer id is required")
    private String customerId;

    @NotBlank(message = "SKU is required")
    private String sku;

    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;
}
