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
import java.util.UUID;

@Table("products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @PrimaryKey("product_id")
    private UUID productId;

    @Column("category_id")
    private UUID categoryId;

    @Column("category_name")
    private String categoryName;

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("sku")
    private String sku;

    @Column("image_url")
    private String imageUrl;

    @Column("brand")
    private String brand;

    @Column("dimensions")
    private String dimensions;

    @Column("status")
    private String status;

    @Column("created_by")
    private String createdBy;

    @Column("updated_by")
    private String updatedBy;

    @Column("price")
    private BigDecimal price;

    @Column("weight")
    private BigDecimal weight;

    @Column("discount_percent")
    private BigDecimal discountPercent;

    @Column("stock_quantity")
    private Integer stockQuantity;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
