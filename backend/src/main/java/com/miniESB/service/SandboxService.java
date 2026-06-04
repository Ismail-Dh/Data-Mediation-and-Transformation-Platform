package com.miniESB.service;

import com.miniESB.dto.Pipeline.PipelineResponse;
import com.miniESB.dto.sandbox.SandboxRequest;
import com.miniESB.dto.sandbox.SandboxResponse;

public interface SandboxService {
    SandboxResponse run(Long pipelineId, SandboxRequest request);
}