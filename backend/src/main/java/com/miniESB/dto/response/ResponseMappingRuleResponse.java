package com.miniESB.dto.response;

import com.miniESB.domain.enums.MappingType;

public record ResponseMappingRuleResponse(
        Long id,
        Long pipelineId,
        Long providerId,
        String providerName,
        String sourceField,
        String targetField,
        MappingType mappingType,
        String expression,
        boolean required,
        boolean active
) {}