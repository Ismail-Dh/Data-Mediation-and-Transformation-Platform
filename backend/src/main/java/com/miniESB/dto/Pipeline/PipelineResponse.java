package com.miniESB.dto.Pipeline;

import java.time.LocalDateTime;
import java.util.List;

public record PipelineResponse(
        Long id,
        String name,
        String version,
        LocalDateTime createdAt,
        String inputFormat,
        String outputFormat,
        String status,
        String createdBy,

        /** Liste des providers attachés — remplace les champs singuliers providerId/providerName. */
        List<ProviderSummary> providers
) {
    /** Résumé d'un provider dans la réponse pipeline (évite une dépendance circulaire). */
    public record ProviderSummary(Long id, String name, String endpoint) {}
}