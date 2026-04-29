package com.miniESB.dto.provider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
public record CreateProviderRequest(
    @NotBlank String name,
    @NotBlank String endpoint,
    @NotBlank String protocol,
    @NotNull @Positive int timeout
) {}
