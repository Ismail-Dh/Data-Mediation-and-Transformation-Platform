package com.miniESB.dto.mapping;

import com.miniESB.domain.enums.MappingType;

// Response DTO returned after any mapping rule operation.
public record MappingRuleResponse(
    Long id,
    String sourceField,
    String targetField,
    MappingType mappingType,
    String expression,
    boolean active,
    Long pipelineId
) {}