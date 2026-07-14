package com.miniESB.service;

import com.miniESB.dto.response.CreateResponseMappingRuleRequest;
import com.miniESB.dto.response.ResponseMappingRuleResponse;

import java.util.List;

/**
 * T6 — CRUD des {@code ResponseMappingRule}.
 *
 * <p>Séparée de {@link ResponseMappingExecutionService} (exécution runtime) pour
 * respecter l'Interface Segregation Principle : {@code ResponseMappingRuleController}
 * n'a besoin que de ces 4 méthodes d'administration et ne devrait pas dépendre
 * de {@code validateAndMap}, qu'il n'appelle jamais.</p>
 */
public interface ResponseMappingRuleAdminService {

    ResponseMappingRuleResponse createRule(Long pipelineId, CreateResponseMappingRuleRequest request);

    List<ResponseMappingRuleResponse> getRules(Long pipelineId);

    ResponseMappingRuleResponse updateRule(Long ruleId, CreateResponseMappingRuleRequest request);

    void deleteRule(Long ruleId);
}
