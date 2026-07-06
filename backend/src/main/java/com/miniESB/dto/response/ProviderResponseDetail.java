package com.miniESB.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Réponse persistée d'un provider, enrichie du résultat de validation
 * et de transformation (T6) appliqué sur son contenu brut.
 */
public record ProviderResponseDetail(
        Long providerId,
        String providerName,
        int httpStatus,
        String rawBody,
        long durationMs,
        boolean dispatchSuccess,
        LocalDateTime receivedAt,

        // ── Résultat T6 ──────────────────────────────────────────────────────
        boolean validationPassed,
        List<String> validationErrors,
        Map<String, Object> mappedBody
) {}