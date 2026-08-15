package com.miniESB.dto.pipelineField;

import java.util.List;

/**
 * Résumé du résultat d'un import de schéma de champs à partir d'un fichier JSON.
 *
 * <p>L'import est "best effort" : une entrée invalide (type inconnu, fieldPath
 * en doublon, etc.) n'annule pas l'import des autres entrées valides du même
 * fichier — elle est simplement reportée dans {@link #errors()}.</p>
 */
public record PipelineFieldImportResponse(
        int totalRequested,
        int created,
        int skipped,
        List<PipelineFieldResponse> fields,
        List<PipelineFieldImportError> errors
) {}