package com.miniESB.dto.pipelineField;

/**
 * Erreur associée à une entrée du fichier JSON qui n'a pas pu être importée
 * (fieldPath manquant, fieldType inconnu, doublon, etc.).
 */
public record PipelineFieldImportError(
        String fieldPath,
        String reason
) {}