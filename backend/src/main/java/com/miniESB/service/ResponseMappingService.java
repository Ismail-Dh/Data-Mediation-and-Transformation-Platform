package com.miniESB.service;

import com.miniESB.dto.response.ProviderResponseDetail;
import com.miniESB.dto.dispatch.ProviderDispatchResult;

import java.util.List;

/**
 * T6 — Valide et transforme les réponses brutes reçues des providers
 * selon les {@code ResponseMappingRule} configurées sur le pipeline.
 *
 * <p>Pour chaque {@link ProviderDispatchResult} (résultat du dispatch),
 * ce service applique les règles actives et produit un {@link ProviderResponseDetail}
 * enrichi avec le résultat de validation et le corps mappé.</p>
 */
public interface ResponseMappingService {

    /**
     * Valide et transforme la liste des résultats de dispatch.
     *
     * @param pipelineId identifiant du pipeline
     * @param results    résultats bruts du dispatch (un par provider)
     * @return liste de détails enrichis, un par provider
     */
    List<ProviderResponseDetail> validateAndMap(Long pipelineId,
                                                List<ProviderDispatchResult> results);

    // ── CRUD des ResponseMappingRule ──────────────────────────────────────────

    com.miniESB.dto.response.ResponseMappingRuleResponse createRule(
            Long pipelineId,
            com.miniESB.dto.response.CreateResponseMappingRuleRequest request);

    List<com.miniESB.dto.response.ResponseMappingRuleResponse> getRules(Long pipelineId);

    com.miniESB.dto.response.ResponseMappingRuleResponse updateRule(
            Long ruleId,
            com.miniESB.dto.response.CreateResponseMappingRuleRequest request);

    void deleteRule(Long ruleId);
}