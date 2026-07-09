package com.miniESB.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.Provider;
import com.miniESB.domain.entity.ResponseMappingRule;
import com.miniESB.domain.enums.MappingType;
import com.miniESB.dto.dispatch.ProviderDispatchResult;
import com.miniESB.dto.response.*;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.repository.ProviderRepository;
import com.miniESB.repository.ResponseMappingRuleRepository;
import com.miniESB.service.ResponseMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * T6 — Implémentation de la validation et du mapping des réponses providers.
 *
 * <p><b>Algorithme par provider :</b></p>
 * <ol>
 *   <li>Désérialisation du rawBody JSON en Map.</li>
 *   <li>Pour chaque règle active applicable (ciblant ce provider ou générique) :
 *     <ul>
 *       <li>Si {@code required=true} et le champ source absent → violation.</li>
 *       <li>Si le champ est présent → applique la transformation selon {@link MappingType}.</li>
 *     </ul>
 *   </li>
 *   <li>Retourne validationPassed=true si aucune violation, plus le corps mappé.</li>
 * </ol>
 *
 * <p>Les réponses en erreur réseau (httpStatus=0) ou HTTP non-2xx ne sont pas
 * validées — elles reçoivent automatiquement validationPassed=false avec
 * un message d'erreur explicite.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)

public class ResponseMappingServiceImpl implements ResponseMappingService {

    private final ResponseMappingRuleRepository ruleRepository;
    private final PipelineRepository            pipelineRepository;
    private final ProviderRepository            providerRepository;
    private final ObjectMapper                  objectMapper;

    // ── T6 : validation + mapping ─────────────────────────────────────────────

    @Override
    public List<ProviderResponseDetail> validateAndMap(Long pipelineId,
                                                       List<ProviderDispatchResult> results) {
        List<ProviderResponseDetail> details = new ArrayList<>();

        for (ProviderDispatchResult result : results) {
            details.add(processOneProvider(pipelineId, result));
        }

        return details;
    }

    private ProviderResponseDetail processOneProvider(Long pipelineId,
                                                      ProviderDispatchResult result) {
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

        // Désérialiser le rawBody
        Map<String, Object> sourceMap;
        try {
            sourceMap = objectMapper.readValue(result.rawBody(),
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Cannot parse provider '{}' response as JSON: {}", result.providerName(), e.getMessage());
            return new ProviderResponseDetail(
                    result.providerId(), result.providerName(),
                    result.httpStatus(), result.rawBody(), result.durationMs(),
                    true, LocalDateTime.now(),
                    false, List.of("Response body is not valid JSON: " + e.getMessage()), Map.of()
            );
        }

        // Charger les règles applicables à ce provider
        List<ResponseMappingRule> rules = ruleRepository.findApplicableRules(
                pipelineId, result.providerId());

        List<String> violations = new ArrayList<>();
        Map<String, Object> mappedBody = new LinkedHashMap<>();

        for (ResponseMappingRule rule : rules) {
            applyRule(rule, sourceMap, mappedBody, violations);
        }

        // Copier les champs non couverts par les règles dans le corps mappé
        // (comportement passthrough : on garde tout, les règles renomment/transforment)
        sourceMap.forEach((k, v) -> mappedBody.putIfAbsent(k, v));

        boolean passed = violations.isEmpty();
        log.info("Provider '{}' response validation: passed={} violations={}",
                result.providerName(), passed, violations.size());

        return new ProviderResponseDetail(
                result.providerId(), result.providerName(),
                result.httpStatus(), result.rawBody(), result.durationMs(),
                true, LocalDateTime.now(),
                passed, violations, mappedBody
        );
    }

    private void applyRule(ResponseMappingRule rule,
                           Map<String, Object> source,
                           Map<String, Object> target,
                           List<String> violations) {

        String srcField = rule.getSourceField();
        String tgtField = rule.getTargetField();
        Object value    = source.get(srcField);

        // Validation : champ requis absent
        if (value == null) {
            if (rule.isRequired()) {
                violations.add("Required field '" + srcField + "' is missing in provider response");
            }
            return;
        }

        // Transformation selon MappingType (mêmes constantes que MappingServiceImpl / T5)
        Object transformed;
        try {
            transformed = switch (rule.getMappingType()) {
                case FIELD_PLACEMENT  -> value;                                   // copie / renommage simple, sans transformation
                case FORMAT_CHANGE    -> applyFormatChange(value, rule.getExpression());
                case VALUE_TRANSFORM  -> applyValueTransform(rule.getExpression(), value, source);
                case CALCULATED_FIELD -> applyConcat(rule.getExpression(), source);
                case RESTRUCTURING    -> value;                                   // pas de restructuration imbriquée pour les réponses
            };
        } catch (Exception e) {
            log.warn("Rule id={} transformation failed for field '{}': {}", rule.getId(), srcField, e.getMessage());
            violations.add("Transformation failed for field '" + srcField + "': " + e.getMessage());
            return;
        }

        target.put(tgtField, transformed);
    }

    /**
     * FORMAT_CHANGE : l'expression indique l'opération de formatage à appliquer
     * (UPPERCASE, LOWERCASE, TRIM). Reprend la même convention que
     * MappingServiceImpl#transformSingleValue pour rester cohérent avec T5.
     */
    private Object applyFormatChange(Object value, String expression) {
        if (expression == null || expression.isBlank()) return value;
        return switch (expression.trim().toUpperCase()) {
            case "UPPERCASE" -> value.toString().toUpperCase();
            case "LOWERCASE" -> value.toString().toLowerCase();
            case "TRIM"      -> value.toString().trim();
            default -> throw new IllegalArgumentException(
                    "Unknown FORMAT_CHANGE expression: " + expression);
        };
    }

    /**
     * VALUE_TRANSFORM : si l'expression commence par "CONSTANT:", retourne une
     * valeur fixe indépendante du champ source ; sinon retourne la valeur telle quelle.
     */
    private Object applyValueTransform(String expression, Object value, Map<String, Object> source) {
        if (expression == null || expression.isBlank()) return value;
        if (expression.startsWith("CONSTANT:")) {
            return expression.substring("CONSTANT:".length());
        }
        return value;
    }

    /**
     * CONCAT : l'expression contient les noms de champs séparés par '+'.
     * Exemple : expression = "firstName+' '+lastName"
     * Support simplifié — concatène les valeurs des champs listés.
     */
    private Object applyConcat(String expression, Map<String, Object> source) {
        if (expression == null || expression.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String part : expression.split("\\+")) {
            String token = part.trim().replace("'", "");
            sb.append(source.getOrDefault(token, token));
        }
        return sb.toString();
    }

    // ── CRUD des ResponseMappingRule ──────────────────────────────────────────

    @Override
    @Transactional
    public ResponseMappingRuleResponse createRule(Long pipelineId,
                                                  CreateResponseMappingRuleRequest req) {
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
        return ruleRepository.findByPipelineId(pipelineId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public ResponseMappingRuleResponse updateRule(Long ruleId,
                                                  CreateResponseMappingRuleRequest req) {
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