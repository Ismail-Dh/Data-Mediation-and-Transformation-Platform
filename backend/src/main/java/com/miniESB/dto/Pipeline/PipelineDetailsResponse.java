package com.miniESB.dto.Pipeline;

import com.miniESB.dto.mapping.MappingRuleResponse;
import com.miniESB.dto.pipelineField.PipelineFieldResponse;
import com.miniESB.dto.pipelineValidationRule.PipelineValidationRuleResponse;
import com.miniESB.dto.response.ResponseMappingRuleResponse;

import java.util.List;

/**
 * Vue consolidée d'un pipeline pour les écrans de supervision (Admin) et de
 * détail (Developer) : regroupe en un seul appel toutes les informations
 * rattachées au pipeline afin d'éviter au frontend de multiplier les
 * requêtes (fields, validation-rules, mappings, response-rules).
 *
 * GET /api/pipelines/{id}/full-details
 */
public record PipelineDetailsResponse(
        PipelineResponse pipeline,

        /** Schéma du payload attendu par le pipeline (Section "Schema Fields"). */
        List<PipelineFieldResponse> fields,

        /** Règles de validation niveau-2 rattachées au pipeline (globales attachées + privées). */
        List<PipelineValidationRuleResponse> validationRules,

        /** Règles de mapping (transform/format/expression) du pipeline, actives et inactives. */
        List<MappingRuleResponse> mappingRules,

        /** Règles de mapping/validation des réponses providers (T6). */
        List<ResponseMappingRuleResponse> responseMappingRules
) {}