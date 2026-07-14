package com.miniESB.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.MappingRule;
import com.miniESB.domain.entity.Payload;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.enums.PayloadStatus;
import com.miniESB.domain.enums.PipelineStatus;
import com.miniESB.dto.mapping.MappingResultResponse;
import com.miniESB.dto.mapping.MappingRuleRequest;
import com.miniESB.dto.mapping.MappingRuleResponse;
import com.miniESB.engine.mapping.MappingEngine;
import com.miniESB.engine.mapping.MappingRuleData;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.MappingRuleRepository;
import com.miniESB.repository.PayloadRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.service.MappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implémentation "mode base de données" de {@link MappingService} : CRUD des
 * {@link MappingRule} + orchestration de leur application via {@link MappingEngine}.
 *
 * <p>Avant refactoring, cette classe (879 lignes) contenait elle-même tout
 * l'interpréteur de mapping (dispatch par type, transformations de valeur,
 * arithmétique, agrégations, navigation JSON...) — violation SRP. Cette logique
 * est désormais dans le package {@code com.miniESB.engine.mapping}, appliquée
 * via le pattern Strategy et partagée avec {@code EngineProcessService} (mode
 * fichier), éliminant la duplication qui existait entre les deux classes.</p>
 */
@Slf4j
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@Service
@RequiredArgsConstructor
public class MappingServiceImpl implements MappingService {

    private final MappingRuleRepository mappingRuleRepository;
    private final PipelineRepository pipelineRepository;
    private final ObjectMapper objectMapper; // injecté par Spring — jamais instancié manuellement
    private final PayloadRepository payloadRepository;
    private final MappingEngine mappingEngine;

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public MappingRuleResponse createRule(Long pipelineId, MappingRuleRequest request) {
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));

        MappingRule rule = MappingRule.builder()
                .sourceField(request.sourceField())
                .targetField(request.targetField())
                .mappingType(request.mappingType())
                .expression(request.expression())
                .active(true)
                .pipeline(pipeline)
                .build();

        MappingRule saved = mappingRuleRepository.save(rule);
        log.info("MappingRule created: id={}, {}→{}, pipeline={}",
                saved.getId(), saved.getSourceField(), saved.getTargetField(), pipelineId);

        // Dirty-checking : pas besoin de save() explicite — Hibernate détecte le changement
        if (pipeline.getStatus() == PipelineStatus.DRAFT) {
            pipeline.setStatus(PipelineStatus.CONFIGURED);
            log.info("Pipeline id={} status updated to CONFIGURED", pipelineId);
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public MappingRuleResponse updateRule(Long pipelineId, Long ruleId, MappingRuleRequest request) {
        MappingRule rule = mappingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MappingRule not found with id=" + ruleId));

        if (!rule.getPipeline().getId().equals(pipelineId)) {
            throw new ResourceNotFoundException(
                    "MappingRule id=" + ruleId + " does not belong to pipeline id=" + pipelineId);
        }

        rule.setSourceField(request.sourceField());
        rule.setTargetField(request.targetField());
        rule.setMappingType(request.mappingType());
        rule.setExpression(request.expression());
        // active status is preserved — update never disables the rule

        MappingRule updated = mappingRuleRepository.save(rule);
        log.info("MappingRule updated: id={}, pipeline={}", ruleId, pipelineId);
        return toResponse(updated);
    }

    @Override
    public List<MappingRuleResponse> getRulesByPipeline(Long pipelineId) {
        pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));
        return mappingRuleRepository.findByPipelineIdAndActiveTrue(pipelineId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<MappingRuleResponse> getAllRulesByPipeline(Long pipelineId) {
        pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));
        return mappingRuleRepository.findByPipelineId(pipelineId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteRule(Long pipelineId, Long ruleId) {
        MappingRule rule = mappingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MappingRule not found with id=" + ruleId));

        if (!rule.getPipeline().getId().equals(pipelineId)) {
            throw new ResourceNotFoundException(
                    "MappingRule id=" + ruleId + " does not belong to pipeline id=" + pipelineId);
        }

        rule.setActive(false); // soft delete — rule is kept in DB for audit purposes
        mappingRuleRepository.save(rule);
        log.info("MappingRule disabled: id={}, pipeline={}", ruleId, pipelineId);
    }

    @Override
    @Transactional
    public MappingResultResponse applyMappingToPayload(Long pipelineId, Long payloadId) {
        pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));

        Payload payload = payloadRepository.findById(payloadId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payload not found with id=" + payloadId));

        if (payload.getStatus() != PayloadStatus.VALIDATED) {
            throw new IllegalStateException(
                    "Payload id=" + payloadId + " must be VALIDATED before mapping — current status: "
                            + payload.getStatus());
        }

        Map<String, Object> input = parseRawContent(payload.getRawContent());
        Map<String, Object> mapped = applyMapping(pipelineId, input);

        payload.setStatus(PayloadStatus.MAPPED);
        payloadRepository.save(payload);
        log.info("Payload mapped: id={}, pipeline={}", payloadId, pipelineId);

        // Le dispatch est piloté par ProcessOrchestrationService (T7), pas ici.
        return new MappingResultResponse(pipelineId, input, mapped);
    }

    @Override
    @Transactional(readOnly = true)
    public String applyAndReturnMapped(Long pipelineId, Long payloadId) {
        Payload payload = payloadRepository.findById(payloadId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payload not found: " + payloadId));

        Map<String, Object> input = parseRawContent(payload.getRawContent());
        Map<String, Object> mapped = applyMapping(pipelineId, input);

        try {
            return objectMapper.writeValueAsString(mapped);
        } catch (Exception e) {
            log.error("Failed to serialize mapped payload id={}: {}", payloadId, e.getMessage());
            return "{}";
        }
    }

    @Override
    @Transactional
    public MappingRuleResponse activateRule(Long pipelineId, Long ruleId) {
        MappingRule rule = mappingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MappingRule not found with id=" + ruleId));

        if (!rule.getPipeline().getId().equals(pipelineId)) {
            throw new ResourceNotFoundException(
                    "MappingRule id=" + ruleId + " does not belong to pipeline id=" + pipelineId);
        }

        rule.setActive(true);
        mappingRuleRepository.save(rule);
        log.info("MappingRule activated: id={}, pipeline={}", ruleId, pipelineId);
        return toResponse(rule);
    }

    // -------------------------------------------------------------------------
    // MAPPING EXECUTION — délègue entièrement à MappingEngine
    // -------------------------------------------------------------------------

    @Override
    public MappingResultResponse applyMappingToPayload(Long pipelineId, String rawContent) {
        pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));

        Map<String, Object> input = parseRawContent(rawContent);
        Map<String, Object> mapped = applyMapping(pipelineId, input);

        log.info("Mapping applied for pipeline={}", pipelineId);
        return new MappingResultResponse(pipelineId, input, mapped);
    }

    private Map<String, Object> applyMapping(Long pipelineId, Map<String, Object> input) {
        List<MappingRuleData> rules = mappingRuleRepository.findByPipelineIdAndActiveTrue(pipelineId)
                .stream()
                .map(this::toRuleData)
                .toList();

        return mappingEngine.apply(rules, input);
    }

    private MappingRuleData toRuleData(MappingRule rule) {
        return new MappingRuleData(rule.getId(), rule.getSourceField(), rule.getTargetField(),
                rule.getMappingType(), rule.getExpression());
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private Map<String, Object> parseRawContent(String rawContent) {
        try {
            return objectMapper.readValue(rawContent, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON content: " + e.getMessage());
        }
    }

    private MappingRuleResponse toResponse(MappingRule rule) {
        return new MappingRuleResponse(
                rule.getId(),
                rule.getSourceField(),
                rule.getTargetField(),
                rule.getMappingType(),
                rule.getExpression(),
                rule.isActive(),
                rule.getPipeline().getId()
        );
    }
}
