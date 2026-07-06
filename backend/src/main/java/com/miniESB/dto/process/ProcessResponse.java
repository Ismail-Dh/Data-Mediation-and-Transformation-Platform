package com.miniESB.dto.process;

import com.miniESB.dto.response.ProviderResponseDetail;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Réponse complète du flux /api/process retournée au consommateur.
 * Contient le résultat agrégé de tous les providers + le détail par provider.
 */
public record ProcessResponse(
        Long payloadId,
        Long pipelineId,
        String pipelineName,
        LocalDateTime processedAt,
        boolean overallSuccess,
        int providerCount,
        int successCount,

        /** Corps agrégé final : union des champs mappés de tous les providers ayant réussi. */
        Map<String, Object> aggregatedBody,

        /** Détail par provider : dispatch + validation/mapping de la réponse. */
        List<ProviderResponseDetail> providerDetails
) {}