package com.miniESB.service;

import com.miniESB.dto.CreateUserRequest;
import com.miniESB.dto.UpdateUserRequest;
import com.miniESB.dto.UserResponse;
import java.util.List;

/**
 * Service interface for user management.
 */
public interface UserService {
    /**
     * Create a new user.
     * @param request DTO containing user data
     * @return UserResponse
     */
    UserResponse createUser(CreateUserRequest request);

    /**
     * Update an existing user (partial update).
     * @param id User ID
     * @param request DTO with fields to update
     * @return UserResponse
     */
    UserResponse updateUser(Long id, UpdateUserRequest request);

    /**
     * Delete a user by ID.
     * @param id User ID
     */
    void deleteUser(Long id);

    /**
     * Assign a role to a user.
     * @param id User ID
     * @param role New role
     * @return UserResponse
     */
    UserResponse assignRole(Long id, String role);

    /**
     * List all users.
     * @return List of UserResponse
     */
    List<UserResponse> getAllUsers();
}