package com.example.project2.repository;

import com.example.project2.entity.OrderDetail;
import com.example.project2.repository.projection.CategoryUserRevenueProjection;
import com.example.project2.repository.projection.OrderDetailReportProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {

    // Joins order_details, orders, users, products and categories - all 5 tables -
    // to list order line items with their order, buyer, product and category info.
    @Query(value = """
            SELECT
                o.id               AS orderId,
                o.order_number     AS orderNumber,
                o.status           AS orderStatus,
                o.payment_status   AS paymentStatus,
                o.total_amount     AS totalAmount,
                o.created_at       AS orderCreatedAt,
                u.id               AS userId,
                u.username         AS username,
                u.email            AS email,
                u.first_name       AS firstName,
                u.last_name        AS lastName,
                od.id              AS orderDetailId,
                od.quantity        AS quantity,
                od.unit_price      AS unitPrice,
                od.discount        AS itemDiscount,
                od.total_price     AS totalPrice,
                p.id               AS productId,
                p.name             AS productName,
                p.sku              AS productSku,
                p.price            AS currentPrice,
                p.stock_quantity   AS stockQuantity,
                c.id               AS categoryId,
                c.name             AS categoryName,
                c.slug             AS categorySlug
            FROM order_details od
            JOIN orders o          ON o.id = od.order_id
            JOIN users u           ON u.id = o.user_id
            JOIN products p        ON p.id = od.product_id
            LEFT JOIN categories c ON c.id = p.category_id
            WHERE (:status IS NULL OR o.status = :status)
              AND o.created_at >= :since
            ORDER BY o.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM order_details od
            JOIN orders o ON o.id = od.order_id
            WHERE (:status IS NULL OR o.status = :status)
              AND o.created_at >= :since
            """,
            nativeQuery = true)
    Page<OrderDetailReportProjection> findOrderDetailReport(
            @Param("status") String status,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    // Same 5-table join, aggregated into monthly revenue per category/user - a
    // heavier query (GROUP BY + aggregates) for performance testing at scale.
    @Query(value = """
            SELECT
                c.name                             AS categoryName,
                u.id                                AS userId,
                u.username                          AS username,
                DATE_TRUNC('month', o.created_at)   AS orderMonth,
                COUNT(DISTINCT o.id)                 AS orderCount,
                SUM(od.quantity)                     AS totalUnits,
                SUM(od.total_price)                  AS totalRevenue,
                AVG(p.price)                          AS avgProductPrice
            FROM order_details od
            JOIN orders o          ON o.id = od.order_id
            JOIN users u           ON u.id = o.user_id
            JOIN products p        ON p.id = od.product_id
            LEFT JOIN categories c ON c.id = p.category_id
            GROUP BY c.name, u.id, u.username, DATE_TRUNC('month', o.created_at)
            ORDER BY totalRevenue DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM (
                SELECT 1
                FROM order_details od
                JOIN orders o          ON o.id = od.order_id
                JOIN users u           ON u.id = o.user_id
                JOIN products p        ON p.id = od.product_id
                LEFT JOIN categories c ON c.id = p.category_id
                GROUP BY c.name, u.id, u.username, DATE_TRUNC('month', o.created_at)
            ) grouped
            """,
            nativeQuery = true)
    Page<CategoryUserRevenueProjection> findCategoryUserRevenueReport(Pageable pageable);
}
