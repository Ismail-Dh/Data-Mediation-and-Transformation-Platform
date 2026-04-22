package com.miniESB.controller;

import com.miniESB.dto.user.CreateUserRequest;
import com.miniESB.dto.user.ResetPasswordRequest;
import com.miniESB.dto.user.UpdateUserRequest;
import com.miniESB.dto.user.UserResponse;
import com.miniESB.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ─── Existing endpoints ───────────────────────────────────────────────────

    @PostMapping("/add")
    public ResponseEntity<UserResponse> createUser(@Validated @RequestBody CreateUserRequest request) {
        return new ResponseEntity<>(userService.createUser(request), HttpStatus.CREATED);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id,
                                                   @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> assignRole(@PathVariable Long id,
                                                   @RequestParam String role) {
        return ResponseEntity.ok(userService.assignRole(id, role));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PatchMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> resetPassword(@PathVariable Long id,
                                                      @Validated @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(userService.resetPassword(id, request.oldPassword(), request.newPassword()));
    }

    // ─── Forgot-password flow (3 steps) ──────────────────────────────────────

    /**
     * Step 1 — Request a reset code.
     * POST /api/users/forgot-password?username=john
     *
     * Always returns 200 OK to avoid leaking whether the username exists.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam String username) {
        userService.forgotPassword(username);
        return ResponseEntity.ok("Code de réinitialisation envoyé à l'adresse email associée.");
    }

    /**
     * Step 2 — Verify the 6-digit code.
     * POST /api/users/verify-code?username=john&code=482910
     *
     * Returns 200 {"valid": true/false}.
     */
    @PostMapping("/verify-code")
    public ResponseEntity<Boolean> verifyCode(@RequestParam String username,
                                              @RequestParam String code) {
        boolean valid = userService.verifyCode(username, code);
        return ResponseEntity.ok(valid);
    }

    /**
     * Step 3 — Set the new password (only after code verification).
     * POST /api/users/reset-password?username=john&newPassword=secret123
     */
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPasswordByCode(@RequestParam String username,
                                                      @RequestParam String newPassword) {
        userService.resetPasswordByCode(username, newPassword);
        return ResponseEntity.ok("Mot de passe modifié avec succès.");
    }
}