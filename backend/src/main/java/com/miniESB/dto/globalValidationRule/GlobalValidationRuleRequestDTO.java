package com.miniESB.dto.globalValidationRule;


import com.miniESB.domain.enums.RuleType;
import io.swagger.v3.oas.annotations.media.Schema;
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

        @Schema(description = "Regex pattern (required for REGEX_CUSTOM)", example = "^[A-Z]{3}\\d{4}$")
        @Size(max = 500, message = "pattern must not exceed 500 characters")
        String pattern,

        @Schema(description = "Whether the rule is active", example = "true")
        boolean active
) {}