package com.miniESB.dto.response;

import com.miniESB.domain.enums.MappingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Corps de création d'une ResponseMappingRule. */
public record CreateResponseMappingRuleRequest(
        @NotBlank String sourceField,
        @NotBlank String targetField,
        @NotNull  MappingType mappingType,
        String expression,
        boolean required,

        /**
         * Provider ciblé (optionnel).
         * null = la règle s'applique aux réponses de tous les providers du pipeline.
         */
        Long providerId
) {}