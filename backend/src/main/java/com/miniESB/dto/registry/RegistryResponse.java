// dto/registry/RegistryResponse.java
package com.miniESB.dto.registry;

public record RegistryResponse(
    Long   id,
    String name,
    String url,
    String username
    // password jamais retourné
) {}