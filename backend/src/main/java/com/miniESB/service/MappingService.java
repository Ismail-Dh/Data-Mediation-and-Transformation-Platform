package com.miniESB.service;

import com.miniESB.dto.mapping.MappingResultResponse;
import com.miniESB.dto.mapping.MappingRuleRequest;
import com.miniESB.dto.mapping.MappingRuleResponse;

import java.util.List;

public interface MappingService {

    // CRUD
    MappingRuleResponse createRule(Long pipelineId, MappingRuleRequest request);
    List<MappingRuleResponse> getRulesByPipeline(Long pipelineId);
    List<MappingRuleResponse> getAllRulesByPipeline(Long pipelineId);
    void deleteRule(Long pipelineId, Long ruleId);
    MappingRuleResponse activateRule(Long pipelineId, Long ruleId);
    MappingRuleResponse updateRule(Long pipelineId, Long ruleId, MappingRuleRequest request);

    // Mapping execution
    MappingResultResponse applyMappingToPayload(Long pipelineId, String rawContent);
    MappingResultResponse applyMappingToPayload(Long pipelineId, Long payloadId);

    /**
     * Applique le mapping et retourne directement le corps mappé sérialisé en JSON.
     * Utilisé par {@code ProcessOrchestrationService} pour récupérer le payload
     * mappé sans passer par le dispatch automatique.
     */
    String applyAndReturnMapped(Long pipelineId, Long payloadId);
}