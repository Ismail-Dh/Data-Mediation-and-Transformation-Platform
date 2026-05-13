package com.miniESB.dto.validation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for the dry-run preview endpoint.
 * Identical structure to PayloadRequest — no persistence occurs.
 */
public record ValidationPreviewRequest(

        @NotBlank(message = "rawContent must not be blank")
        String rawContent,

        @NotNull(message = "format is required")
        String format   // JSON, XML, CSV, PLAIN_TEXT — validated structurally only for JSON
) {}