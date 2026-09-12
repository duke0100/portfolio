package com.example.project3.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("categories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @PrimaryKey("category_id")
    private UUID categoryId;

    @Column("name")
    private String name;

    @Column("slug")
    private String slug;

    @Column("status")
    private String status;

    @Column("created_at")
    private Instant createdAt;
}
