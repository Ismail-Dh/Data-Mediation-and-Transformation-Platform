// dto/docker/PushImageRequest.java
package com.miniESB.dto.docker;

import jakarta.validation.constraints.NotNull;

public record PushImageRequest(
    @NotNull Long registryId
) {}