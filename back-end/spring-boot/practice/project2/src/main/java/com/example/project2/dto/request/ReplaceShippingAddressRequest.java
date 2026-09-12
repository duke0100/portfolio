package com.example.project2.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#put-and-delete
 * Body of the PUT that replaces an order's shipping address.
 *
 * <p>Both fields are required on purpose. A PUT sends the whole new state, so a missing field means
 * "clear it", not "leave it alone" - that is what makes sending it twice safe.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplaceShippingAddressRequest {

    @NotBlank(message = "Shipping address is required")
    @Size(max = 500, message = "Shipping address must be at most 500 characters")
    private String shippingAddress;

    @NotBlank(message = "Billing address is required")
    @Size(max = 500, message = "Billing address must be at most 500 characters")
    private String billingAddress;
}
