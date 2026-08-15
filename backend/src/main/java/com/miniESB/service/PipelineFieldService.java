package com.miniESB.service;

import com.miniESB.dto.pipelineField.PipelineFieldImportResponse;
import com.miniESB.dto.pipelineField.PipelineFieldRequest;
import com.miniESB.dto.pipelineField.PipelineFieldResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PipelineFieldService {
    PipelineFieldResponse addField(Long pipelineId, PipelineFieldRequest request);
    List<PipelineFieldResponse> getFields(Long pipelineId);
    PipelineFieldResponse updateField(Long pipelineId, Long fieldId, PipelineFieldRequest request);
    void deleteField(Long pipelineId, Long fieldId);

    /**
     * Importe en masse les champs du schéma d'un pipeline à partir d'un
     * fichier JSON décrivant un tableau d'entrées
     * {@code { fieldPath, fieldType, required }}.
     *
     * <p>Import "best effort" : les entrées invalides ou en doublon sont
     * ignorées et reportées dans le résultat plutôt que de faire échouer
     * l'ensemble de l'import.</p>
     */
    PipelineFieldImportResponse importFieldsFromJson(Long pipelineId, MultipartFile file);
}