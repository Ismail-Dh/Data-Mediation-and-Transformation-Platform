package com.miniESB.dto.auth;

import com.miniESB.audit.Sensitive;

public record RegisterRequest(String username, @Sensitive String password, String role) {}

