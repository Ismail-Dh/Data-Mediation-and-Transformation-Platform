package com.miniESB.dto.Pipeline;

import com.miniESB.domain.enums.HttpRequestMethod;
import jakarta.validation.constraints.NotNull;

/**
 * Sélection d'un provider à attacher à un pipeline, avec la méthode HTTP
 * (GET, POST, PUT, PATCH) à utiliser pour lui transmettre le payload mappé.
 *
 * Remplace le simple {@code Long providerId} — la méthode HTTP se choisit
 * désormais ici, à la création/édition du pipeline (pas dans l'écran de
 * gestion des providers).
 */
public record PipelineProviderRequest(
        @NotNull Long providerId,
        /** Optionnel — POST appliqué par défaut si non fourni. */
        HttpRequestMethod httpMethod
) {}