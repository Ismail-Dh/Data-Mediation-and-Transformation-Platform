package com.miniESB.dto.pipelineValidationRule;

import com.miniESB.domain.enums.RuleType;

/**
 * Response for a pipeline-scoped validation rule.
 * global=true means it was originally an Admin rule attached to this pipeline.
 * global=false means the Developer created it specifically for this pipeline.
 */
public record PipelineValidationRuleResponse(
        Long id,
        String fieldName,
        RuleType ruleType,
        String pattern,
        String description,
        boolean active,
        boolean global,
        Long pipelineId
) {}