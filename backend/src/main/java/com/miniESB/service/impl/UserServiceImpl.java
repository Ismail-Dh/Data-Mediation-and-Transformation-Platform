package com.miniESB.service.impl;

import com.miniESB.domain.entity.User;
import com.miniESB.domain.enums.UserRole;
import com.miniESB.dto.CreateUserRequest;
import com.miniESB.dto.UpdateUserRequest;
import com.miniESB.dto.UserResponse;
import com.miniESB.repository.UserRepository;
import com.miniESB.service.UserService;
import com.miniESB.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DataIntegrityViolationException("Username already exists");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.valueOf(request.role()));
        user = userRepository.save(user);
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (request.username() != null) user.setUsername(request.username());
        if (request.password() != null) user.setPasswordHash(passwordEncoder.encode(request.password()));
        if (request.role() != null) user.setRole(UserRole.valueOf(request.role()));
        user = userRepository.save(user);
        return toResponse(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found");
        }
        userRepository.deleteById(id);
    }

    @Override
    @Transactional
    public UserResponse assignRole(Long id, String role) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setRole(UserRole.valueOf(role));
        user = userRepository.save(user);
        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getRole().name()
        );
    }
}