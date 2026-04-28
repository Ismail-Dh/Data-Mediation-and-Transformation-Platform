package com.miniESB.dto.Pipeline;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePipelineRequest(
    @NotBlank String name,
    String version,
    @NotNull String inputFormat,
    @NotNull String outputFormat,

    Long providerId,

    String providerName,
    String providerEndpoint,
    String providerProtocol,
    Integer providerTimeout
) {}