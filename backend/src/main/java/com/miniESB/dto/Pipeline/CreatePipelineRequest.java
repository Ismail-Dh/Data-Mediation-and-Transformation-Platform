package com.miniESB.dto.Pipeline;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreatePipelineRequest(
        @NotBlank String name,
        String version,
        @NotNull String inputFormat,
        @NotNull String outputFormat,

        /**
         * Liste des providers à attacher au pipeline.
         * Remplace l'ancien champ singulier {@code providerId}.
         * Peut être vide ou null si aucun provider n'est encore configuré.
         */
        List<Long> providerIds
) {}