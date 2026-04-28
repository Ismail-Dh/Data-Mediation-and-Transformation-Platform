package com.miniESB.dto.Pipeline;

public record UpdatePipelineRequest(
    String name,
    String providerUrl,
    String version,
    String inputFormat,
    String outputFormat,
    Long providerId,
    String providerName,
    String providerEndpoint,
    String providerProtocol,
    Integer providerTimeout
) {}
