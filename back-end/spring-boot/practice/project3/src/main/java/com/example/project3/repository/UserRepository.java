package com.example.project3.repository;

import com.example.project3.entity.User;
import org.springframework.data.cassandra.repository.AllowFiltering;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserRepository extends CassandraRepository<User, UUID> {

    @AllowFiltering
    List<User> findByEmail(String email);

    @AllowFiltering
    List<User> findByUsername(String username);
}
