package com.example.project1.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project1.dto.request.CreateOrderRequest;
import com.example.project1.dto.request.UpdateOrderRequest;
import com.example.project1.dto.response.OrderResponse;
import com.example.project1.entity.Order;
import com.example.project1.entity.OrderDetail;
import com.example.project1.entity.Product;
import com.example.project1.entity.User;
import com.example.project1.repository.OrderDetailRepository;
import com.example.project1.repository.OrderRepository;
import com.example.project1.repository.ProductRepository;
import com.example.project1.repository.UserRepository;
import com.example.project1.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderDetailRepository orderDetailRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAll(Pageable pageable) {
        log.info("Fetching all orders with pageable: {}", pageable);
        return orderRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        log.info("Fetching order by id: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Order not found with id: " + id));
        return toResponse(order);
    }

    @Override
    public OrderResponse create(CreateOrderRequest request) {
        log.info("Creating order for userId: {}", request.getUserId());
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + request.getUserId()));

        BigDecimal shippingFee = request.getShippingFee() != null ? request.getShippingFee() : BigDecimal.ZERO;
        BigDecimal discountAmount = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;

        Order order = Order.builder()
                .user(user)
                .orderNumber("ORD-" + System.currentTimeMillis())
                .status("PENDING")
                .shippingAddress(request.getShippingAddress())
                .billingAddress(request.getBillingAddress())
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus("UNPAID")
                .notes(request.getNotes())
                .shippingFee(shippingFee)
                .discountAmount(discountAmount)
                .estimatedDeliveryDate(request.getEstimatedDeliveryDate())
                .totalAmount(BigDecimal.ZERO)
                .build();

        Order savedOrder = orderRepository.save(order);

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderDetail> details = new ArrayList<>();

        for (CreateOrderRequest.OrderItemRequest item : request.getItems()) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> BusinessException.notFound("Product not found with id: " + item.getProductId()));

            BigDecimal unitPrice = product.getPrice();
            BigDecimal itemDiscount = item.getDiscount() != null ? item.getDiscount() : BigDecimal.ZERO;
            BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())).subtract(itemDiscount);

            subtotal = subtotal.add(totalPrice);

            OrderDetail detail = OrderDetail.builder()
                    .order(savedOrder)
                    .product(product)
                    .quantity(item.getQuantity())
                    .unitPrice(unitPrice)
                    .discount(itemDiscount)
                    .totalPrice(totalPrice)
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .notes(item.getNotes())
                    .taxAmount(BigDecimal.ZERO)
                    .build();

            details.add(detail);
        }

        orderDetailRepository.saveAll(details);

        BigDecimal totalAmount = subtotal.add(shippingFee).subtract(discountAmount);
        savedOrder.setTotalAmount(totalAmount);
        Order finalOrder = orderRepository.save(savedOrder);

        log.info("Order created with id: {}", finalOrder.getId());
        return toResponse(finalOrder);
    }

    @Override
    public OrderResponse update(Long id, UpdateOrderRequest request) {
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
        Order saved = orderRepository.save(order);
        log.info("Order updated with id: {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    public void delete(Long id) {
        log.info("Deleting order with id: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Order not found with id: " + id));
        orderRepository.delete(order);
        log.info("Order deleted with id: {}", id);
    }

    private OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUser() != null ? order.getUser().getId() : null)
                .userEmail(order.getUser() != null ? order.getUser().getEmail() : null)
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
