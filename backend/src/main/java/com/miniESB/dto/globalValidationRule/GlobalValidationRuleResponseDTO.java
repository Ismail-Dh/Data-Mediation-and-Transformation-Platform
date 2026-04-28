package com.miniESB.dto.globalValidationRule;


import com.miniESB.domain.enums.RuleType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response payload for a global validation rule")
public record GlobalValidationRuleResponseDTO(

        @Schema(description = "Unique identifier of the rule", example = "1")
        Long id,

        @Schema(description = "Name of the field to validate", example = "email")
        String fieldName,

        @Schema(description = "Type of validation rule", example = "REGEX_EMAIL")
        RuleType ruleType,

        @Schema(description = "Regex pattern if applicable")
        String pattern,

        @Schema(description = "Whether the rule is currently active", example = "true")
        boolean active,

        @Schema(description = "Whether this rule is global (always true for admin-managed rules)", example = "true")
        boolean global
) {}