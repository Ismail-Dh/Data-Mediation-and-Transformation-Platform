package com.miniESB.dto.payload;

import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.PayloadStatus;

import java.time.LocalDateTime;

public record PayloadResponse(
    Long id,
    String rawContent,
    DataFormat format,
    PayloadStatus status,
    LocalDateTime receivedAt,
    Long pipelineId
) {}