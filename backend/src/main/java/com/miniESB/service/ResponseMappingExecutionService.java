package com.miniESB.service;

import com.miniESB.dto.dispatch.ProviderDispatchResult;
import com.miniESB.dto.response.ProviderResponseDetail;

import java.util.List;

/**
 * T6 — Valide et transforme les réponses brutes reçues des providers, au moment
 * de l'exécution d'un pipeline.
 *
 * <p>Séparée de {@link ResponseMappingRuleAdminService} (CRUD des règles) pour
 * respecter l'Interface Segregation Principle : {@code ProcessOrchestrationServiceImpl}
 * n'a besoin que de {@code validateAndMap} et ne devrait pas dépendre des
 * méthodes d'administration des règles (create/update/delete), qu'il n'appelle
 * jamais.</p>
 */
public interface ResponseMappingExecutionService {

    List<ProviderResponseDetail> validateAndMap(Long pipelineId, List<ProviderDispatchResult> results);
}
