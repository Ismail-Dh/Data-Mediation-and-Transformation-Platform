package com.miniESB.dto.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PayloadRequest(
    @NotBlank String rawContent,
    @NotNull String format        // "JSON", "XML" etc — correspond à DataFormat enum
) {}