package com.example.project3.service.impl;

import com.example.commonlib.exception.BusinessException;
import com.example.project3.dto.request.CreateUserRequest;
import com.example.project3.dto.request.UpdateUserRequest;
import com.example.project3.dto.response.UserResponse;
import com.example.project3.entity.User;
import com.example.project3.repository.UserRepository;
import com.example.project3.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public List<UserResponse> findAll() {
        log.info("Fetching all users");
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public UserResponse findById(UUID id) {
        log.info("Fetching user with id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + id));
        return toResponse(user);
    }

    @Override
    public UserResponse create(CreateUserRequest request) {
        log.info("Creating user with email: {}", request.getEmail());

        if (!userRepository.findByEmail(request.getEmail()).isEmpty()) {
            throw BusinessException.conflict("Email already in use: " + request.getEmail());
        }

        if (!userRepository.findByUsername(request.getUsername()).isEmpty()) {
            throw BusinessException.conflict("Username already taken: " + request.getUsername());
        }

        User user = User.builder()
                .userId(UUID.randomUUID())
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(request.getPassword())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .address(request.getAddress())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .avatarUrl(request.getAvatarUrl())
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();

        User saved = userRepository.save(user);
        log.info("User created with id: {}", saved.getUserId());
        return toResponse(saved);
    }

    @Override
    public UserResponse update(UUID id, UpdateUserRequest request) {
        log.info("Updating user with id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + id));

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getAddress() != null) {
            user.setAddress(request.getAddress());
        }
        if (request.getDateOfBirth() != null) {
            user.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        user.setUpdatedAt(Instant.now());

        User saved = userRepository.save(user);
        log.info("User updated with id: {}", saved.getUserId());
        return toResponse(saved);
    }

    @Override
    public void delete(UUID id) {
        log.info("Deleting user with id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + id));
        userRepository.delete(user);
        log.info("User deleted with id: {}", id);
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .address(user.getAddress())
                .dateOfBirth(user.getDateOfBirth())
                .gender(user.getGender())
                .status(user.getStatus())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
