package com.example.project2.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#where-it-happens
     * Lazy-loaded, which is exactly why reading it for every order in a page causes the N+1
     * problem unless the query fetches it upfront.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#batch-fetching
     * {@code @BatchSize(50)} loads up to 50 orders' details in one query instead of one query per
     * order. No cascade here on purpose - details are saved through their own repository.
     */
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @Builder.Default
    private List<OrderDetail> details = new ArrayList<>();

    @Column(name = "order_number", length = 50, unique = true, nullable = false)
    private String orderNumber;

    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "total_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_status", length = 30)
    @Builder.Default
    private String paymentStatus = "UNPAID";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "shipping_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "estimated_delivery_date")
    private LocalDate estimatedDeliveryDate;

    @Column(name = "actual_delivery_date")
    private LocalDate actualDeliveryDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#syncing-both-sides
     * There is no cascade on details, so this only fixes memory and the detail still needs saving through its repository.
     */
    public void addDetail(OrderDetail detail) {
        details.add(detail);
        detail.setOrder(this);
    }

    /** Without orphanRemoval this drops the detail from memory but never deletes the row. */
    public void removeDetail(OrderDetail detail) {
        details.remove(detail);
        detail.setOrder(null);
    }
}
