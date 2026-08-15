// dto/registry/CreateRegistryRequest.java
package com.miniESB.dto.registry;

import com.miniESB.audit.Sensitive;
import jakarta.validation.constraints.NotBlank;

public record CreateRegistryRequest(
        @NotBlank String name,
        @NotBlank String url,
        @NotBlank String username,
        @NotBlank @Sensitive String password
) {}