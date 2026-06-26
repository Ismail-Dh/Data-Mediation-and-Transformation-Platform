// dto/docker/PushImageResponse.java
package com.miniESB.dto.docker;

public record PushImageResponse(
    Long    pipelineId,
    Long    registryId,
    String  registryName,
    String  imageFullName,  // ex: registry.hub.docker.com/ayaas/pipeline-8:1.2.3
    String  status,         // SUCCESS | FAILED
    String  message
) {}