package com.miniESB.dto.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PayloadRequest(
    @NotBlank String rawContent,
    @NotNull String format       
) {}