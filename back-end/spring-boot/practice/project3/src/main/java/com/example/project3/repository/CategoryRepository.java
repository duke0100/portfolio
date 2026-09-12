package com.example.project3.repository;

import com.example.project3.entity.Category;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CategoryRepository extends CassandraRepository<Category, UUID> {
}
