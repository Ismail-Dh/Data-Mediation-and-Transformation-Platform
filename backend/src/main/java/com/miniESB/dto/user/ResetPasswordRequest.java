package com.miniESB.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank
    String oldPassword,
    @NotBlank
    @Size(min = 8)
    String newPassword
) {}