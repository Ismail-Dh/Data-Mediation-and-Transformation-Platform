package com.miniESB.dto.globalValidationRule;

import com.miniESB.domain.enums.RuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for creating or updating a global validation rule")
public record GlobalValidationRuleRequestDTO(

        @Schema(description = "Name of the field to validate", example = "email")
        @NotBlank(message = "fieldName must not be blank")
        @Size(max = 150, message = "fieldName must not exceed 150 characters")
        String fieldName,

        @Schema(description = "Type of validation rule", example = "REGEX_EMAIL")
        @NotNull(message = "ruleType is required")
        RuleType ruleType,

        @Schema(description = "Regex pattern (required for REGEX_*, MIN_MAX_LENGTH, TYPE_NUMBER, TYPE_DATE)",
                example = "^[A-Z]{3}\\d{4}$")
        @Size(max = 500, message = "pattern must not exceed 500 characters")
        String pattern,

        @Schema(description = "Whether the rule is active", example = "true")
        boolean active,

        @Schema(description = "Optional description")
        @Size(max = 500)
        String description

) {
    @AssertTrue(message = "pattern is required for ruleType: REGEX_EMAIL, REGEX_PHONE, REGEX_PASSWORD, REGEX, MIN_MAX_LENGTH, TYPE_NUMBER, TYPE_DATE")
    public boolean isPatternValid() {
        return switch (ruleType) {
            case REGEX_EMAIL, REGEX_PHONE, REGEX_PASSWORD, REGEX, MIN_MAX_LENGTH, TYPE_NUMBER, TYPE_DATE ->
                    pattern != null && !pattern.isBlank();
            case NOT_NULL -> true;
        };
    }
}