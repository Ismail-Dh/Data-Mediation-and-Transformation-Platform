package com.miniESB.dto;

public record UpdateUserRequest(
    String username,
    String password,
    String role
) {}