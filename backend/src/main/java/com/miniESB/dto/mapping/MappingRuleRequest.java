package com.miniESB.dto.mapping;

import com.miniESB.domain.enums.MappingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Request DTO for creating a mapping rule.
public record MappingRuleRequest(
    @NotBlank String sourceField,
    @NotBlank String targetField,
    @NotNull  MappingType mappingType,
    String expression

) {}