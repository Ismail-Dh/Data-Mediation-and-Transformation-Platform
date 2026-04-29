package com.miniESB.dto.provider;


public record ProviderResponse(
    Long id,
    String name,
    String endpoint,
    String protocol,
    int timeout
) {}
