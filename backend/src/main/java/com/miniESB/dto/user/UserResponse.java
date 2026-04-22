package com.miniESB.dto.user;

public record UserResponse(
    Long id,
    String username,
    String role
) {}