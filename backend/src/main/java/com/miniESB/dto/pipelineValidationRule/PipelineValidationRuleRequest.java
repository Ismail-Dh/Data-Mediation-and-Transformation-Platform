package com.miniESB.dto.pipelineValidationRule;

import com.miniESB.domain.enums.RuleType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body to attach or create a validation rule on a pipeline.
 *
 * Two modes:
 *  - Pick a global rule: supply globalRuleId (other fields are ignored and copied from global)
 *  - Create a private rule: leave globalRuleId null and fill fieldName + ruleType [+ pattern]
 *
 * Pattern is required for: REGEX_EMAIL, REGEX_PHONE, REGEX_PASSWORD, REGEX, MIN_MAX_LENGTH, TYPE_NUMBER, TYPE_DATE.
 * Pattern is ignored for:  NOT_NULL.
 */
public record PipelineValidationRuleRequest(

        /**
         * Optional — ID of an existing global rule to attach to this pipeline.
         * When set, fieldName / ruleType / pattern / description are copied from the global rule.
         */
        Long globalRuleId,

        @NotBlank(message = "fieldName must not be blank")
        @Size(max = 150)
        String fieldName,

        @NotNull(message = "ruleType is required")
        RuleType ruleType,

        @Size(max = 500)
        String pattern,

        @Size(max = 500)
        String description,

        boolean active
) {
    @AssertTrue(message = "pattern is required for ruleType: REGEX_EMAIL, REGEX_PHONE, REGEX_PASSWORD, REGEX, MIN_MAX_LENGTH, TYPE_NUMBER, TYPE_DATE (when creating a private rule without globalRuleId)")
    public boolean isPatternValid() {
        // If we're referencing a global rule, pattern validation is skipped
        if (globalRuleId != null) return true;

        return switch (ruleType) {
            case REGEX_EMAIL, REGEX_PHONE, REGEX_PASSWORD, REGEX, MIN_MAX_LENGTH, TYPE_NUMBER, TYPE_DATE ->
                    pattern != null && !pattern.isBlank();
            case NOT_NULL -> true;
        };
    }
}