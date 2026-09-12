package com.example.project3.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Table("orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @PrimaryKey("order_id")
    private UUID orderId;

    @Column("user_id")
    private UUID userId;

    @Column("user_email")
    private String userEmail;

    @Column("order_number")
    private String orderNumber;

    @Column("status")
    private String status;

    @Column("shipping_address")
    private String shippingAddress;

    @Column("billing_address")
    private String billingAddress;

    @Column("payment_method")
    private String paymentMethod;

    @Column("payment_status")
    private String paymentStatus;

    @Column("notes")
    private String notes;

    @Column("created_by")
    private String createdBy;

    @Column("updated_by")
    private String updatedBy;

    @Column("total_amount")
    private BigDecimal totalAmount;

    @Column("shipping_fee")
    private BigDecimal shippingFee;

    @Column("discount_amount")
    private BigDecimal discountAmount;

    @Column("estimated_delivery_date")
    private LocalDate estimatedDeliveryDate;

    @Column("actual_delivery_date")
    private LocalDate actualDeliveryDate;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
