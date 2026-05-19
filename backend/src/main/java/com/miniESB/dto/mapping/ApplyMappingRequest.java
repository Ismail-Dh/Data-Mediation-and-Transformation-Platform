package com.miniESB.dto.mapping;

import jakarta.validation.constraints.NotBlank;

// Request DTO for the /apply endpoint.
public record ApplyMappingRequest(
    @NotBlank String rawContent
) {}