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
         * Liste des providers à attacher au pipeline, chacun avec sa propre
         * méthode HTTP (GET/POST/PUT/PATCH) — remplace l'ancien {@code providerIds}
         * (simple liste d'IDs, toujours envoyée en POST).
         * Peut être vide ou null si aucun provider n'est encore configuré.
         */
        List<PipelineProviderRequest> providers
) {}