package com.miniESB.dto.pipelineField;

import com.miniESB.domain.enums.FieldType;

public record PipelineFieldResponse(
    Long id,
    String fieldPath,
    FieldType fieldType,
    boolean required,
    boolean nullable
) {}