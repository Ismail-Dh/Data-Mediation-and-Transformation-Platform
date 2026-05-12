package com.miniESB.dto.pipelineField;

import com.miniESB.domain.enums.FieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PipelineFieldRequest(
    @NotBlank String fieldPath,
    @NotNull FieldType fieldType,
    boolean required,
    boolean nullable
) {}