// dto/docker/BuildLogEntryResponse.java
package com.miniESB.dto.docker;

import com.miniESB.domain.enums.ImageStatus;
import java.time.LocalDateTime;

public record BuildLogEntryResponse(
    Long          id,
    String        tag,
    String        version,
    ImageStatus   status,
    LocalDateTime startTime,
    LocalDateTime endTime,
    Long          durationSeconds
) {}