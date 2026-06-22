// dto/registry/UpdateRegistryRequest.java
package com.miniESB.dto.registry;

public record UpdateRegistryRequest(
    String name,
    String url,
    String username,
    String password
) {}