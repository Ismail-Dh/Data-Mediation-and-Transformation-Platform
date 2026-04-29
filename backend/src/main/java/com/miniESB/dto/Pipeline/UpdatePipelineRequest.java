package com.miniESB.dto.Pipeline;

public record UpdatePipelineRequest(
    String name,
    String version,
    String inputFormat,
    String outputFormat,
    Long providerId
) {}
