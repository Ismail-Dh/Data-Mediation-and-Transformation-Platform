package com.miniESB.dto.template;

import com.miniESB.domain.enums.TemplateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Payload used by Admin to create or update a template (DRAFT state only).
 */
public record TemplateRequest(

        @NotBlank(message = "name is required")
        @Size(max = 200)
        String name,

        @Size(max = 500)
        String description,

        @NotNull(message = "type is required (VALIDATION or MAPPING)")
        TemplateType type,

        /**
         * Free-form JSON object whose structure depends on the template type.
         * For VALIDATION: { "rules": [ { "field": "...", "ruleType": "...", "pattern": "..." } ] }
         * For MAPPING:    { "mappings": [ { "source": "...", "target": "...", "type": "..." } ] }
         */
        @NotNull(message = "content must not be null")
        Map<String, Object> content
) {}