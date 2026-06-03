package com.miniESB.dto.sandbox;

import jakarta.validation.constraints.NotBlank;

public record SandboxRequest(
    @NotBlank String rawContent,
    @NotBlank String format
) {}