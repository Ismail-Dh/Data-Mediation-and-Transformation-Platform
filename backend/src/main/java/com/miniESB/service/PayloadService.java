package com.miniESB.service;

import com.miniESB.dto.payload.PayloadRequest;
import com.miniESB.dto.payload.PayloadResponse;

import java.util.List;

public interface PayloadService {
    PayloadResponse receivePayload(Long pipelineId, PayloadRequest request);
    List<PayloadResponse> getPayloadsByPipeline(Long pipelineId);
    PayloadResponse getPayloadById(Long id);
}