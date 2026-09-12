package com.example.project2.service;

import com.example.project2.dto.request.CreateUserRequest;
import com.example.project2.dto.request.UpdateUserRequest;
import com.example.project2.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    Page<UserResponse> findAll(Pageable pageable);

    UserResponse findById(Long id);

    UserResponse create(CreateUserRequest request);

    UserResponse update(Long id, UpdateUserRequest request);

    void delete(Long id);
}
