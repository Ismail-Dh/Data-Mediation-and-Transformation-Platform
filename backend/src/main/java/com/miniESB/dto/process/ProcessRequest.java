package com.miniESB.dto.process;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Corps de la requête POST /api/process.
 * Déclenche le flux complet : validation → mapping → dispatch → agrégation.
 */
public record ProcessRequest(
        @NotNull(message = "pipelineId is required")
        Long pipelineId,

        @NotBlank(message = "rawContent must not be blank")
        String rawContent,

        /** Format du payload entrant (JSON, XML, CSV). Par défaut JSON. */
        String inputFormat
) {}