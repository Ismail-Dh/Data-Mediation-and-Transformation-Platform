package com.miniESB.dto.Pipeline;

import java.time.LocalDateTime;

public record PipelineResponse(
    Long id,
    String name,
    String version,
    LocalDateTime createdAt,
    String inputFormat,
    String outputFormat,
    String status,
    String createdBy,
    Long providerId,
    String providerName,
    String providerEndpoint

) {}
