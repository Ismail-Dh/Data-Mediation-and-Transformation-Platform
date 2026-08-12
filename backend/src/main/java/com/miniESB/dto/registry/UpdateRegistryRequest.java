// dto/registry/UpdateRegistryRequest.java
package com.miniESB.dto.registry;

import com.miniESB.audit.Sensitive;

public record UpdateRegistryRequest(
        String name,
        String url,
        String username,
        @Sensitive String password
) {}