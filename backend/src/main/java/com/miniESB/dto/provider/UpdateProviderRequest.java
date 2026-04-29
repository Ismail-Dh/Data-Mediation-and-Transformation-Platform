package com.miniESB.dto.provider;


public record UpdateProviderRequest(
    String name,
    String endpoint,
    String protocol,
    Integer timeout
) {}
