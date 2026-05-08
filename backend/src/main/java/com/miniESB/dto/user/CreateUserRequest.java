
package com.miniESB.dto.user;

import com.miniESB.audit.Sensitive;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @NotBlank String username,
    @Sensitive
    @NotBlank @Size(min = 8) String password,
    @NotNull String role,
    @NotBlank String email
) {}