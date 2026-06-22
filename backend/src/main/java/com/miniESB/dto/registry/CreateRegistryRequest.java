// dto/registry/CreateRegistryRequest.java
package com.miniESB.dto.registry;

import jakarta.validation.constraints.NotBlank;

public record CreateRegistryRequest(
    @NotBlank String name,
    @NotBlank String url,
    @NotBlank String username,
    @NotBlank String password
) {}