package com.miniESB.dto.docker;

import com.miniESB.domain.enums.ImageStatus;
import java.time.LocalDateTime;

public record DockerImageResponse(
    Long id,
    String imageName,
    String tag,
    ImageStatus status,
    Long sizeBytes,
    LocalDateTime builtAt,
    Long pipelineId
) {}