package com.miniESB.controller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.miniESB.audit.Auditable;
import com.miniESB.audit.Sensitive;
import com.miniESB.dto.user.CreateUserRequest;
import com.miniESB.dto.user.ResetPasswordRequest;
import com.miniESB.dto.user.UpdateUserRequest;
import com.miniESB.dto.user.UserResponse;
import com.miniESB.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Users", description = "User management")
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Create a user", description = "Creates a new user. Publicly accessible.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created successfully"),
            @ApiResponse(responseCode = "409", description = "Username already exists"),
            @ApiResponse(responseCode = "400", description = "Invalid data")
    })
    @PostMapping("/add")
    @Auditable(action = "CREATE_USER", targetEntity = "User")
    public ResponseEntity<UserResponse> createUser(@Validated @RequestBody CreateUserRequest request) {
        return new ResponseEntity<>(userService.createUser(request), HttpStatus.CREATED);
    }

    @Operation(summary = "Update a user", description = "Partial update of a user. ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "UPDATE_USER", targetEntity = "User")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id,
                                                   @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @Operation(summary = "Delete a user", description = "Delete a user by ID. ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deleted successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "DELETE_USER", targetEntity = "User")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Assign a role", description = "Update a user's role. ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role assigned successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "ASSIGN_ROLE", targetEntity = "User")
    public ResponseEntity<UserResponse> assignRole(@PathVariable Long id,
                                                   @RequestParam String role) {
        return ResponseEntity.ok(userService.assignRole(id, role));
    }

    @Operation(summary = "List all users", description = "Returns the full list of users. ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List returned successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "READ_ALL", targetEntity = "User")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(summary = "Reset password (Admin)", description = "Admin resets a user's password. ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PatchMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "RESET_PASSWORD", targetEntity = "User")
    public ResponseEntity<UserResponse> resetPassword(@PathVariable Long id,
                                                      @Validated @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(userService.resetPassword(id, request.oldPassword(), request.newPassword()));
    }

    @Operation(summary = "Request reset code", description = "Sends a reset code to the associated email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Code sent")
    })
    @PostMapping("/forgot-password")
    @Auditable(action = "FORGOT_PASSWORD", targetEntity = "User")
    public ResponseEntity<String> forgotPassword(@RequestParam String username) {
        userService.forgotPassword(username);
        return ResponseEntity.ok("Reset code sent to the associated email address.");
    }

    @Operation(summary = "Verify reset code", description = "Checks if the 6-digit code is valid.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification result: true or false")
    })
    @PostMapping("/verify-code")
    @Auditable(action = "VERIFY_CODE", targetEntity = "User")
    public ResponseEntity<Boolean> verifyCode(@RequestParam String username,
                                              @Sensitive @RequestParam String code) {
        return ResponseEntity.ok(userService.verifyCode(username, code));
    }

    @Operation(summary = "Set new password via code", description = "Sets a new password after code validation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired code")
    })
    @PostMapping("/reset-password")
    @Auditable(action = "RESET_PASSWORD_BY_CODE", targetEntity = "User")
    public ResponseEntity<String> resetPasswordByCode(@RequestParam String username,
                                                      @Sensitive @RequestParam String newPassword) {
        userService.resetPasswordByCode(username, newPassword);
        return ResponseEntity.ok("Password updated successfully");
    }
}