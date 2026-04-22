
package com.miniESB.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @NotBlank String username,
    @NotBlank @Size(min = 8) String password,
    @NotNull String role
) {}