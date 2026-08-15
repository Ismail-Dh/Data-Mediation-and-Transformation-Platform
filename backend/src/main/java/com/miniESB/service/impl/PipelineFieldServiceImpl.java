package com.miniESB.service.impl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.PipelineField;
import com.miniESB.domain.enums.FieldType;
import com.miniESB.dto.pipelineField.PipelineFieldImportError;
import com.miniESB.dto.pipelineField.PipelineFieldImportItem;
import com.miniESB.dto.pipelineField.PipelineFieldImportResponse;
import com.miniESB.dto.pipelineField.PipelineFieldRequest;
import com.miniESB.dto.pipelineField.PipelineFieldResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.PipelineFieldRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.service.PipelineFieldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@Service
@RequiredArgsConstructor
public class PipelineFieldServiceImpl implements PipelineFieldService {

    private final PipelineFieldRepository pipelineFieldRepository;
    private final PipelineRepository pipelineRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public PipelineFieldResponse addField(Long pipelineId, PipelineFieldRequest request) {
        Pipeline pipeline = findPipelineOrThrow(pipelineId);

        // eviter les doublons sur le même fieldPath
        if (pipelineFieldRepository.existsByPipelineIdAndFieldPath(pipelineId, request.fieldPath())) {
            throw new IllegalArgumentException(
                    "Field path '" + request.fieldPath() + "' already exists on this pipeline");
        }

        PipelineField field = PipelineField.builder()
                .fieldPath(request.fieldPath())
                .fieldType(request.fieldType())
                .required(request.required())
                .nullable(!request.required())
                .pipeline(pipeline)
                .build();

        PipelineField saved = pipelineFieldRepository.save(field);
        log.info("PipelineField created: id={}, path={}, pipeline={}", saved.getId(), saved.getFieldPath(), pipelineId);
        return toResponse(saved);
    }

    @Override
    public List<PipelineFieldResponse> getFields(Long pipelineId) {
        findPipelineOrThrow(pipelineId);
        return pipelineFieldRepository.findAllByPipelineId(pipelineId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public PipelineFieldResponse updateField(Long pipelineId, Long fieldId, PipelineFieldRequest request) {
        PipelineField field = findFieldOrThrow(pipelineId, fieldId);

        // si le fieldPath change, on verifie qu'il n'existe pas deja//handle from db
        if (!field.getFieldPath().equals(request.fieldPath()) &&
                pipelineFieldRepository.existsByPipelineIdAndFieldPath(pipelineId, request.fieldPath())) {
            throw new IllegalArgumentException(
                    "Field path '" + request.fieldPath() + "' already exists on this pipeline");
        }

        field.setFieldPath(request.fieldPath());
        field.setFieldType(request.fieldType());
        field.setRequired(request.required());
        field.setNullable(!request.required());

        PipelineField updated = pipelineFieldRepository.save(field);
        log.info("PipelineField updated: id={}, pipeline={}", updated.getId(), pipelineId);
        return toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteField(Long pipelineId, Long fieldId) {
        PipelineField field = findFieldOrThrow(pipelineId, fieldId);
        pipelineFieldRepository.delete(field);
        log.info("PipelineField deleted: id={}, pipeline={}", fieldId, pipelineId);
    }

    @Override
    @Transactional
    public PipelineFieldImportResponse importFieldsFromJson(Long pipelineId, MultipartFile file) {
        Pipeline pipeline = findPipelineOrThrow(pipelineId);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename != null && !filename.toLowerCase().endsWith(".json")) {
            throw new IllegalArgumentException("Only .json files are accepted");
        }

        List<PipelineFieldImportItem> items;
        try {
            items = objectMapper.readValue(
                    file.getInputStream(),
                    new TypeReference<List<PipelineFieldImportItem>>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Invalid JSON file: expected an array of { fieldPath, fieldType, required } — " + e.getMessage());
        }

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("The JSON file does not define any field");
        }

        List<PipelineFieldResponse> created = new ArrayList<>();
        List<PipelineFieldImportError> errors = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();

        for (PipelineFieldImportItem item : items) {
            try {
                PipelineField field = buildFieldFromImportItem(pipelineId, pipeline, item, seenInFile);
                PipelineField saved = pipelineFieldRepository.save(field);
                created.add(toResponse(saved));
            } catch (IllegalArgumentException ex) {
                errors.add(new PipelineFieldImportError(
                        item != null ? item.fieldPath() : null, ex.getMessage()));
            }
        }

        log.info("PipelineField JSON import: pipeline={}, requested={}, created={}, errors={}",
                pipelineId, items.size(), created.size(), errors.size());

        return new PipelineFieldImportResponse(
                items.size(), created.size(), errors.size(), created, errors);
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private PipelineField buildFieldFromImportItem(
            Long pipelineId, Pipeline pipeline, PipelineFieldImportItem item, Set<String> seenInFile) {

        if (item == null) {
            throw new IllegalArgumentException("Empty entry in JSON file");
        }
        if (item.fieldPath() == null || item.fieldPath().isBlank()) {
            throw new IllegalArgumentException("fieldPath is required");
        }
        if (item.fieldType() == null || item.fieldType().isBlank()) {
            throw new IllegalArgumentException("fieldType is required");
        }

        FieldType fieldType;
        try {
            fieldType = FieldType.valueOf(item.fieldType().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown fieldType '" + item.fieldType() + "'");
        }

        if (!seenInFile.add(item.fieldPath())) {
            throw new IllegalArgumentException("Duplicate fieldPath in the JSON file");
        }
        if (pipelineFieldRepository.existsByPipelineIdAndFieldPath(pipelineId, item.fieldPath())) {
            throw new IllegalArgumentException(
                    "Field path '" + item.fieldPath() + "' already exists on this pipeline");
        }

        return PipelineField.builder()
                .fieldPath(item.fieldPath())
                .fieldType(fieldType)
                .required(item.required())
                .nullable(!item.required())
                .pipeline(pipeline)
                .build();
    }

    private Pipeline findPipelineOrThrow(Long pipelineId) {
        return pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));
    }

    private PipelineField findFieldOrThrow(Long pipelineId, Long fieldId) {
        PipelineField field = pipelineFieldRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PipelineField not found with id=" + fieldId));
        if (!field.getPipeline().getId().equals(pipelineId)) {
            throw new ResourceNotFoundException(
                    "PipelineField id=" + fieldId + " does not belong to pipeline id=" + pipelineId);
        }
        return field;
    }

    private PipelineFieldResponse toResponse(PipelineField field) {
        return new PipelineFieldResponse(
                field.getId(),
                field.getFieldPath(),
                field.getFieldType(),
                field.isRequired(),
                field.isNullable()
        );
    }
}