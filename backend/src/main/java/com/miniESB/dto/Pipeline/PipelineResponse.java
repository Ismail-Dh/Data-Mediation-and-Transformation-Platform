package com.miniESB.dto.Pipeline;

import com.miniESB.domain.enums.HttpRequestMethod;

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
    /** Résumé d'un provider attaché à un pipeline, incluant la méthode HTTP choisie pour cette liaison. */
    public record ProviderSummary(Long id, String name, String endpoint, HttpRequestMethod httpMethod) {}
}