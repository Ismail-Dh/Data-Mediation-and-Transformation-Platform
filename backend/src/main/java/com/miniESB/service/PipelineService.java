package com.miniESB.service;

import com.miniESB.dto.Pipeline.CreatePipelineRequest;
import com.miniESB.dto.Pipeline.PipelineResponse;
import com.miniESB.dto.Pipeline.UpdatePipelineRequest;

import java.util.List;

public interface PipelineService {
    PipelineResponse createPipeline(CreatePipelineRequest request, String username);
    PipelineResponse updatePipeline(Long id, UpdatePipelineRequest request, String username);
    void deletePipeline(Long id, String username);
    PipelineResponse getPipelineById(Long id, String username);
    List<PipelineResponse> getMyPipelines(String username);
    List<PipelineResponse> getAllPipelines();
    PipelineResponse validatePipeline(Long pipelineId);
PipelineResponse revertPipeline(Long pipelineId);
}
