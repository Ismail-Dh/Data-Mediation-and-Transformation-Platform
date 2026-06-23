package com.miniESB.dto.docker;

/**
 * Réponse retournée après un bump de version manuel.
 */
public record VersionBumpResponse(
        Long   pipelineId,
        String previousVersion,
        String newVersion,
        String message
) {}