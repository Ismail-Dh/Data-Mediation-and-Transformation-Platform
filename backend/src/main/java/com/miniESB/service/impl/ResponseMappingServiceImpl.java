package com.miniESB.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.Provider;
import com.miniESB.domain.entity.ResponseMappingRule;
import com.miniESB.dto.dispatch.ProviderDispatchResult;
import com.miniESB.dto.response.*;
import com.miniESB.engine.responsemapping.ResponseFieldRuleData;
import com.miniESB.engine.responsemapping.ResponseMappingEngine;
import com.miniESB.engine.responsemapping.ResponseMappingResult;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.repository.ProviderRepository;
import com.miniESB.repository.ResponseMappingRuleRepository;
import com.miniESB.service.ResponseMappingExecutionService;
import com.miniESB.service.ResponseMappingRuleAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * T6 — Implémentation "mode base de données" de la validation et du mapping
 * des réponses providers.
 *
 * <p>Implémente deux interfaces distinctes ({@link ResponseMappingExecutionService},
 * {@link ResponseMappingRuleAdminService}) plutôt qu'une seule interface fourre-tout
 * (Interface Segregation Principle) : {@code ProcessOrchestrationServiceImpl} ne
 * dépend que de l'exécution, {@code ResponseMappingRuleController} ne dépend que
 * du CRUD.</p>
 *
 * <p><strong>Depuis le refactoring</strong>, le dispatch par {@code MappingType}
 * et l'implémentation de chaque transformation ne sont plus ici : ils vivent
 * dans {@code com.miniESB.engine.responsemapping} (pattern Strategy), partagés
 * avec {@code EngineResponseMappingHelper} (mode fichier), qui dupliquait
 * auparavant ce même code pour opérer sans JPA.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
public class ResponseMappingServiceImpl implements ResponseMappingExecutionService, ResponseMappingRuleAdminService {

    private final ResponseMappingRuleRepository ruleRepository;
    private final PipelineRepository pipelineRepository;
    private final ProviderRepository providerRepository;
    private final ObjectMapper objectMapper;
    private final ResponseMappingEngine responseMappingEngine;

    // ── T6 : validation + mapping (ResponseMappingExecutionService) ─────────

    @Override
    public List<ProviderResponseDetail> validateAndMap(Long pipelineId,
                                                         List<ProviderDispatchResult> results) {
        List<ProviderResponseDetail> details = new ArrayList<>();
        for (ProviderDispatchResult result : results) {
            details.add(processOneProvider(pipelineId, result));
        }
        return details;
    }

    private ProviderResponseDetail processOneProvider(Long pipelineId, ProviderDispatchResult result) {
        // Réponse en erreur réseau ou HTTP non-2xx → pas de validation
        if (!result.success()) {
            String reason = result.httpStatus() == 0
                    ? result.errorMessage()
                    : "HTTP " + result.httpStatus() + " — provider returned error";

            return new ProviderResponseDetail(
                    result.providerId(), result.providerName(),
                    result.httpStatus(), result.rawBody(), result.durationMs(),
                    false, LocalDateTime.now(),
                    false, List.of("Dispatch failed: " + reason), Map.of()
            );
        }

        Map<String, Object> sourceMap;
        try {
            sourceMap = objectMapper.readValue(result.rawBody(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Cannot parse provider '{}' response as JSON: {}", result.providerName(), e.getMessage());
            return new ProviderResponseDetail(
                    result.providerId(), result.providerName(),
                    result.httpStatus(), result.rawBody(), result.durationMs(),
                    true, LocalDateTime.now(),
                    false, List.of("Response body is not valid JSON: " + e.getMessage()), Map.of()
            );
        }

        List<ResponseFieldRuleData> ruleData = ruleRepository.findApplicableRules(pipelineId, result.providerId())
                .stream()
                .map(this::toRuleData)
                .toList();

        ResponseMappingResult mapping = responseMappingEngine.apply(ruleData, sourceMap);

        log.info("Provider '{}' response validation: passed={} violations={}",
                result.providerName(), mapping.passed(), mapping.violations().size());

        return new ProviderResponseDetail(
                result.providerId(), result.providerName(),
                result.httpStatus(), result.rawBody(), result.durationMs(),
                true, LocalDateTime.now(),
                mapping.passed(), mapping.violations(), mapping.mappedBody()
        );
    }

    private ResponseFieldRuleData toRuleData(ResponseMappingRule rule) {
        return new ResponseFieldRuleData(rule.getId(), rule.getSourceField(), rule.getTargetField(),
                rule.getMappingType(), rule.getExpression(), rule.isRequired());
    }

    // ── CRUD des ResponseMappingRule (ResponseMappingRuleAdminService) ──────

    @Override
    @Transactional
    public ResponseMappingRuleResponse createRule(Long pipelineId, CreateResponseMappingRuleRequest req) {
        var pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found: " + pipelineId));

        Provider provider = null;
        if (req.providerId() != null) {
            provider = providerRepository.findById(req.providerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Provider not found: " + req.providerId()));
        }

        var rule = ResponseMappingRule.builder()
                .pipeline(pipeline)
                .provider(provider)
                .sourceField(req.sourceField())
                .targetField(req.targetField())
                .mappingType(req.mappingType())
                .expression(req.expression())
                .required(req.required())
                .active(true)
                .build();

        return toResponse(ruleRepository.save(rule));
    }

    @Override
    public List<ResponseMappingRuleResponse> getRules(Long pipelineId) {
        return ruleRepository.findByPipelineId(pipelineId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public ResponseMappingRuleResponse updateRule(Long ruleId, CreateResponseMappingRuleRequest req) {
        var rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("ResponseMappingRule not found: " + ruleId));

        rule.setSourceField(req.sourceField());
        rule.setTargetField(req.targetField());
        rule.setMappingType(req.mappingType());
        rule.setExpression(req.expression());
        rule.setRequired(req.required());

        if (req.providerId() != null) {
            var provider = providerRepository.findById(req.providerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Provider not found: " + req.providerId()));
            rule.setProvider(provider);
        } else {
            rule.setProvider(null);
        }

        return toResponse(ruleRepository.save(rule));
    }

    @Override
    @Transactional
    public void deleteRule(Long ruleId) {
        if (!ruleRepository.existsById(ruleId))
            throw new ResourceNotFoundException("ResponseMappingRule not found: " + ruleId);
        ruleRepository.deleteById(ruleId);
    }

    private ResponseMappingRuleResponse toResponse(ResponseMappingRule r) {
        return new ResponseMappingRuleResponse(
                r.getId(),
                r.getPipeline().getId(),
                r.getProvider() != null ? r.getProvider().getId() : null,
                r.getProvider() != null ? r.getProvider().getName() : null,
                r.getSourceField(), r.getTargetField(),
                r.getMappingType(), r.getExpression(),
                r.isRequired(), r.isActive()
        );
    }
}
