package com.example.project2.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project2.dto.request.CreateOrderRequest;
import com.example.project2.dto.request.ReplaceShippingAddressRequest;
import com.example.project2.dto.request.UpdateOrderRequest;
import com.example.project2.dto.response.CategoryUserRevenueResponse;
import com.example.project2.dto.response.OrderDetailReportResponse;
import com.example.project2.dto.response.OrderResponse;
import com.example.project2.entity.Order;
import com.example.project2.entity.OrderDetail;
import com.example.project2.entity.Product;
import com.example.project2.entity.User;
import com.example.project2.repository.OrderDetailRepository;
import com.example.project2.repository.OrderRepository;
import com.example.project2.repository.ProductRepository;
import com.example.project2.repository.UserRepository;
import com.example.project2.repository.projection.CategoryUserRevenueProjection;
import com.example.project2.repository.projection.OrderDetailReportProjection;
import com.example.project2.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        log.debug("Fetching all orders with pageable: {}", pageable);
        return orderRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        log.debug("Fetching order by id: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Order not found with id: " + id));
        return toResponse(order);
    }

    @Override
    public OrderResponse create(CreateOrderRequest request) {
        log.debug("Creating order for userId: {}", request.getUserId());
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + request.getUserId()));

        BigDecimal shippingFee = request.getShippingFee() != null ? request.getShippingFee() : BigDecimal.ZERO;
        BigDecimal discountAmount = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;

        Order order = Order.builder()
                .user(user)
                .orderNumber("ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8))
                .shippingAddress(request.getShippingAddress())
                .billingAddress(request.getBillingAddress())
                .paymentMethod(request.getPaymentMethod())
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
            BigDecimal totalPrice = unitPrice
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .subtract(itemDiscount);

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
                    .build();

            details.add(detail);
        }

        orderDetailRepository.saveAll(details);

        BigDecimal totalAmount = subtotal.add(shippingFee).subtract(discountAmount);
        savedOrder.setTotalAmount(totalAmount);
        Order finalOrder = orderRepository.save(savedOrder);

        log.info("Created order with id: {} and orderNumber: {}", finalOrder.getId(), finalOrder.getOrderNumber());
        return toResponse(finalOrder);
    }

    @Override
    public OrderResponse update(Long id, UpdateOrderRequest request) {
        log.debug("Updating order with id: {}", id);
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
        log.info("Updated order with id: {}", saved.getId());
        return toResponse(saved);
    }

    /**
     * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#put-and-delete
     * The state after this call depends only on the request body, never on the state before it,
     * so a retried PUT is harmless.
     */
    @Override
    public OrderResponse replaceAddresses(Long id, ReplaceShippingAddressRequest request) {
        log.debug("Replacing addresses on order id: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Order not found with id: " + id));

        order.setShippingAddress(request.getShippingAddress());
        order.setBillingAddress(request.getBillingAddress());

        Order saved = orderRepository.save(order);
        log.info("Replaced addresses on order id: {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    public void delete(Long id) {
        log.debug("Deleting order with id: {}", id);
        if (!orderRepository.existsById(id)) {
            throw BusinessException.notFound("Order not found with id: " + id);
        }
        orderRepository.deleteById(id);
        log.info("Deleted order with id: {}", id);
    }

    /**
     * Interview topic: docs/interview/rest-api/04-idempotency-in-rest-apis.md#put-and-delete
     * Returns false instead of throwing when the order is already gone - the caller turns that into
     * the same 204 the first DELETE returned.
     */
    @Override
    public boolean deleteIfExists(Long id) {
        log.debug("Deleting order if present, id: {}", id);
        if (!orderRepository.existsById(id)) {
            log.debug("Order {} already deleted, nothing to do", id);
            return false;
        }
        orderRepository.deleteById(id);
        log.info("Deleted order with id: {}", id);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderDetailReportResponse> findOrderDetailReport(String status, LocalDateTime since, Pageable pageable) {
        log.debug("Fetching order detail report - status: {}, since: {}", status, since);
        LocalDateTime effectiveSince = since != null ? since : LocalDateTime.now().minusDays(30);
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return orderDetailRepository.findOrderDetailReport(status, effectiveSince, unsorted)
                .map(this::toReportResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CategoryUserRevenueResponse> findCategoryUserRevenueReport(Pageable pageable) {
        log.debug("Fetching category/user revenue report");
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return orderDetailRepository.findCategoryUserRevenueReport(unsorted)
                .map(this::toRevenueResponse);
    }

    private OrderDetailReportResponse toReportResponse(OrderDetailReportProjection p) {
        return OrderDetailReportResponse.builder()
                .orderId(p.getOrderId())
                .orderNumber(p.getOrderNumber())
                .orderStatus(p.getOrderStatus())
                .paymentStatus(p.getPaymentStatus())
                .totalAmount(p.getTotalAmount())
                .orderCreatedAt(p.getOrderCreatedAt())
                .userId(p.getUserId())
                .username(p.getUsername())
                .email(p.getEmail())
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .orderDetailId(p.getOrderDetailId())
                .quantity(p.getQuantity())
                .unitPrice(p.getUnitPrice())
                .itemDiscount(p.getItemDiscount())
                .totalPrice(p.getTotalPrice())
                .productId(p.getProductId())
                .productName(p.getProductName())
                .productSku(p.getProductSku())
                .currentPrice(p.getCurrentPrice())
                .stockQuantity(p.getStockQuantity())
                .categoryId(p.getCategoryId())
                .categoryName(p.getCategoryName())
                .categorySlug(p.getCategorySlug())
                .build();
    }

    private CategoryUserRevenueResponse toRevenueResponse(CategoryUserRevenueProjection p) {
        return CategoryUserRevenueResponse.builder()
                .categoryName(p.getCategoryName())
                .userId(p.getUserId())
                .username(p.getUsername())
                .orderMonth(p.getOrderMonth())
                .orderCount(p.getOrderCount())
                .totalUnits(p.getTotalUnits())
                .totalRevenue(p.getTotalRevenue())
                .avgProductPrice(p.getAvgProductPrice())
                .build();
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
