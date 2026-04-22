package com.miniESB.dto.user;

public record UpdateUserRequest(
    String username,
    String password,
    String role
) {}