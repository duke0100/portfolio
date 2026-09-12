package com.example.project3.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderRequest {

    private String status;

    private String shippingAddress;

    private String billingAddress;

    private String paymentMethod;

    private String paymentStatus;

    private String notes;

    private LocalDate estimatedDeliveryDate;

    private LocalDate actualDeliveryDate;
}
