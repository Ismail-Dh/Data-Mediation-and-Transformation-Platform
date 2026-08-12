package com.miniESB.dto.auth;

import com.miniESB.audit.Sensitive;

public record LoginRequest(String username, @Sensitive String password) {}

