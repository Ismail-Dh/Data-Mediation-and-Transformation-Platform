package com.miniESB.dto.pipelineValidationRule;

import com.miniESB.domain.enums.RuleType;
import jakarta.validation.constraints.AssertTrue;

public record PipelineValidationRuleRequest(

        Long globalRuleId,
        String fieldName,
        RuleType ruleType,
        String pattern,
        String description,
        boolean active

) {
    @AssertTrue(message = "fieldName and ruleType are required when not attaching a global rule")
    public boolean isPrivateRuleValid() {
        if (globalRuleId != null) return true;
        return fieldName != null && !fieldName.isBlank() && ruleType != null;
    }

    @AssertTrue(message = "pattern is required for this ruleType")
    public boolean isPatternValid() {
        if (globalRuleId != null) return true;
        if (ruleType == null) return true;
        return switch (ruleType) {
            case REGEX_EMAIL, REGEX_PHONE, REGEX_PASSWORD,
                 REGEX, MIN_MAX_LENGTH, TYPE_NUMBER, TYPE_DATE ->
                    pattern != null && !pattern.isBlank();
            case NOT_NULL -> true;
        };
    }
}