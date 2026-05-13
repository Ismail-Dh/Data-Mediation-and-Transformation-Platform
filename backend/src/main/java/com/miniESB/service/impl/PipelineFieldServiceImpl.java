package com.miniESB.service.impl;

import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.PipelineField;
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

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineFieldServiceImpl implements PipelineFieldService {

    private final PipelineFieldRepository pipelineFieldRepository;
    private final PipelineRepository pipelineRepository;

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
                .nullable(request.nullable())
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
        field.setNullable(request.nullable());

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

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

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