package com.miniESB.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.MappingRule;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.enums.MappingType;
import com.miniESB.dto.mapping.MappingResultResponse;
import com.miniESB.dto.mapping.MappingRuleRequest;
import com.miniESB.dto.mapping.MappingRuleResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.MappingRuleRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.service.MappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MappingServiceImpl implements MappingService {

    private final MappingRuleRepository mappingRuleRepository;
    private final PipelineRepository    pipelineRepository;
    private final ObjectMapper          objectMapper; // injected by Spring — do not instantiate manually

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
                .active(true)
                .pipeline(pipeline)
                .build();

        MappingRule saved = mappingRuleRepository.save(rule);
        log.info("MappingRule created: id={}, {}→{}, pipeline={}",
                saved.getId(), saved.getSourceField(), saved.getTargetField(), pipelineId);
        return toResponse(saved);
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

        // Ensure the rule belongs to the given pipeline — prevents cross-pipeline operations
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
    public MappingRuleResponse activateRule(Long pipelineId, Long ruleId) {
        MappingRule rule = mappingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MappingRule not found with id=" + ruleId));

        // Ensure the rule belongs to the given pipeline — prevents cross-pipeline operations
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
    // MAPPING EXECUTION
    // -------------------------------------------------------------------------

    @Override
    public MappingResultResponse applyMappingToPayload(Long pipelineId, String rawContent) {
        pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));

        Map<String, Object> input  = parseRawContent(rawContent);
        Map<String, Object> mapped = applyMapping(pipelineId, input);

        log.info("Mapping applied for pipeline={}", pipelineId);
        return new MappingResultResponse(pipelineId, input, mapped);
    }

    // Applies active FIELD_PLACEMENT rules to the parsed input.
    // Fields without a matching rule are passed through unchanged.
    private Map<String, Object> applyMapping(Long pipelineId, Map<String, Object> input) {
        List<MappingRule> rules = mappingRuleRepository.findByPipelineIdAndActiveTrue(pipelineId);

        if (rules.isEmpty()) {
            log.warn("Pipeline id={} has no MappingRule defined — returning input as-is", pipelineId);
            return input;
        }

        // Copy all fields first — unmapped fields are kept as-is
        Map<String, Object> output = new HashMap<>(input);

        for (MappingRule rule : rules) {
            if (rule.getMappingType() == MappingType.FIELD_PLACEMENT) {
                Object value = input.get(rule.getSourceField());
                if (value == null) {
                    log.warn("MappingRule id={} — sourceField '{}' not found in input",
                            rule.getId(), rule.getSourceField());
                    continue;
                }
                output.remove(rule.getSourceField()); // remove old field name
                output.put(rule.getTargetField(), value); // insert new field name
                log.debug("Mapped '{}' → '{}'", rule.getSourceField(), rule.getTargetField());
            }
        }

        return output;
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    // Parses a raw JSON string into a Map. Throws if the content is not valid JSON.
    private Map<String, Object> parseRawContent(String rawContent) {
        try {
            return objectMapper.readValue(rawContent, new TypeReference<>() {});
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
                rule.isActive(),
                rule.getPipeline().getId()
        );
    }
}