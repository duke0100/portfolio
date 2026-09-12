package com.example.project3.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project3.dto.request.CreateOrderRequest;
import com.example.project3.dto.request.UpdateOrderRequest;
import com.example.project3.dto.response.OrderResponse;
import com.example.project3.entity.Order;
import com.example.project3.repository.OrderRepository;
import com.example.project3.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;

    @Override
    public List<OrderResponse> findAll() {
        log.info("Fetching all orders");
        return orderRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public OrderResponse findById(UUID id) {
        log.info("Fetching order with id: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Order not found with id: " + id));
        return toResponse(order);
    }

    @Override
    public OrderResponse create(CreateOrderRequest request) {
        log.info("Creating order for user: {}", request.getUserId());

        BigDecimal itemsTotal = request.getItems().stream()
                .map(item -> {
                    BigDecimal lineTotal = item.getUnitPrice()
                            .multiply(BigDecimal.valueOf(item.getQuantity()));
                    BigDecimal discount = item.getDiscount() != null ? item.getDiscount() : BigDecimal.ZERO;
                    return lineTotal.subtract(discount);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal shippingFee = request.getShippingFee() != null ? request.getShippingFee() : BigDecimal.ZERO;
        BigDecimal discountAmount = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal totalAmount = itemsTotal.add(shippingFee).subtract(discountAmount);

        Order order = Order.builder()
                .orderId(UUID.randomUUID())
                .orderNumber("ORD-" + System.currentTimeMillis())
                .userId(request.getUserId())
                .userEmail(request.getUserEmail())
                .status("PENDING")
                .paymentStatus("UNPAID")
                .shippingAddress(request.getShippingAddress())
                .billingAddress(request.getBillingAddress())
                .paymentMethod(request.getPaymentMethod())
                .notes(request.getNotes())
                .shippingFee(shippingFee)
                .discountAmount(discountAmount)
                .totalAmount(totalAmount)
                .estimatedDeliveryDate(request.getEstimatedDeliveryDate())
                .createdAt(Instant.now())
                .build();

        Order saved = orderRepository.save(order);
        log.info("Order created with id: {}", saved.getOrderId());
        return toResponse(saved);
    }

    @Override
    public OrderResponse update(UUID id, UpdateOrderRequest request) {
        log.info("Updating order with id: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Order not found with id: " + id));

        if (request.getStatus() != null) {
            order.setStatus(request.getStatus());
        }
        if (request.getShippingAddress() != null) {
            order.setShippingAddress(request.getShippingAddress());
        }
        if (request.getBillingAddress() != null) {
            order.setBillingAddress(request.getBillingAddress());
        }
        if (request.getPaymentMethod() != null) {
            order.setPaymentMethod(request.getPaymentMethod());
        }
        if (request.getPaymentStatus() != null) {
            order.setPaymentStatus(request.getPaymentStatus());
        }
        if (request.getNotes() != null) {
            order.setNotes(request.getNotes());
        }
        if (request.getEstimatedDeliveryDate() != null) {
            order.setEstimatedDeliveryDate(request.getEstimatedDeliveryDate());
        }
        if (request.getActualDeliveryDate() != null) {
            order.setActualDeliveryDate(request.getActualDeliveryDate());
        }

        order.setUpdatedAt(Instant.now());

        Order saved = orderRepository.save(order);
        log.info("Order updated with id: {}", saved.getOrderId());
        return toResponse(saved);
    }

    @Override
    public void delete(UUID id) {
        log.info("Deleting order with id: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Order not found with id: " + id));
        orderRepository.delete(order);
        log.info("Order deleted with id: {}", id);
    }

    private OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .userEmail(order.getUserEmail())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .shippingAddress(order.getShippingAddress())
                .billingAddress(order.getBillingAddress())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .notes(order.getNotes())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .estimatedDeliveryDate(order.getEstimatedDeliveryDate())
                .actualDeliveryDate(order.getActualDeliveryDate())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
