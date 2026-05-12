package com.miniESB.service;

import com.miniESB.dto.pipelineField.PipelineFieldRequest;
import com.miniESB.dto.pipelineField.PipelineFieldResponse;

import java.util.List;

public interface PipelineFieldService {
    PipelineFieldResponse addField(Long pipelineId, PipelineFieldRequest request);
    List<PipelineFieldResponse> getFields(Long pipelineId);
    PipelineFieldResponse updateField(Long pipelineId, Long fieldId, PipelineFieldRequest request);
    void deleteField(Long pipelineId, Long fieldId);
}