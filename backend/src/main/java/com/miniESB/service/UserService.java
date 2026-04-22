package com.miniESB.service;

import com.miniESB.dto.user.CreateUserRequest;
import com.miniESB.dto.user.UpdateUserRequest;
import com.miniESB.dto.user.UserResponse;

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

    /**
     * Reset password using old password (authenticated user).
     * @param id User ID
     * @param oldPassword Current password
     * @param newPassword New password
     * @return UserResponse
     */
    UserResponse resetPassword(Long id, String oldPassword, String newPassword);

    /**
     * Send a 6-digit verification code to the user's email.
     * @param username Username of the user requesting the reset
     */
    void forgotPassword(String username);

    /**
     * Verify the reset code sent by email.
     * @param username Username of the user
     * @param code     6-digit code to verify
     * @return true if the code is valid and not expired, false otherwise
     */
    boolean verifyCode(String username, String code);

    /**
     * Reset password after successful code verification.
     * @param username    Username of the user
     * @param newPassword New password to set
     */
    void resetPasswordByCode(String username, String newPassword);
}