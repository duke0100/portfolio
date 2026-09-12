package com.example.project3.service;

import com.example.project3.dto.request.CreateUserRequest;
import com.example.project3.dto.request.UpdateUserRequest;
import com.example.project3.dto.response.UserResponse;

import java.util.List;
import java.util.UUID;

public interface UserService {

    List<UserResponse> findAll();

    UserResponse findById(UUID id);

    UserResponse create(CreateUserRequest request);

    UserResponse update(UUID id, UpdateUserRequest request);

    void delete(UUID id);
}
