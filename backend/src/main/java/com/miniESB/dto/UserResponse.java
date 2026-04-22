package com.miniESB.dto;

public record UserResponse(
    Long id,
    String username,
    String role
) {}