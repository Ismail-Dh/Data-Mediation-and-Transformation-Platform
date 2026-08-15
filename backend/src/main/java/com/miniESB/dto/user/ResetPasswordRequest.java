package com.miniESB.dto.user;

import com.miniESB.audit.Sensitive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank
        @Sensitive
        String oldPassword,
        @NotBlank
        @Size(min = 8)
        @Sensitive
        String newPassword
) {}