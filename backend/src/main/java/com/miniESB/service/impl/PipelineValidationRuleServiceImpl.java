package com.miniESB.service.impl;

import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.ValidationRule;
import com.miniESB.domain.enums.PipelineStatus;
import com.miniESB.dto.pipelineValidationRule.PipelineValidationRuleRequest;
import com.miniESB.dto.pipelineValidationRule.PipelineValidationRuleResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.repository.ValidationRuleRepository;
import com.miniESB.service.PipelineValidationRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineValidationRuleServiceImpl implements PipelineValidationRuleService {

    private final ValidationRuleRepository validationRuleRepository;
    private final PipelineRepository       pipelineRepository;

    // ── ADD ───────────────────────────────────────────────────────────────────
@Override
    @Transactional
    public PipelineValidationRuleResponse addRule(Long pipelineId, PipelineValidationRuleRequest request) {
        Pipeline pipeline = findPipelineOrThrow(pipelineId);

        ValidationRule rule;

        if (request.globalRuleId() != null) {
            // ── Mode 1: attach an existing global rule ────────────────────────
            ValidationRule global = validationRuleRepository.findById(request.globalRuleId())
                    .filter(ValidationRule::isGlobal)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Global rule not found with id=" + request.globalRuleId()));

            // Copy the global rule's definition into a new pipeline-scoped entry
            rule = ValidationRule.builder()
                    .fieldName(global.getFieldName())
                    .ruleType(global.getRuleType())
                    .pattern(global.getPattern())
                    .description(global.getDescription())
                    .active(true)
                    .global(true)        // marks origin as global
                    .pipeline(pipeline)
                    .build();

            log.info("Attaching global rule id={} to pipeline id={}", request.globalRuleId(), pipelineId);

        } else {
            // ── Mode 2: create a private rule ────────────────────────────────
            if (validationRuleRepository.existsByPipelineIdAndFieldNameAndRuleType(
                    pipelineId, request.fieldName(), request.ruleType())) {
                throw new IllegalArgumentException(
                        "A rule of type " + request.ruleType()
                                + " already exists for field '" + request.fieldName()
                                + "' on this pipeline");
            }

            rule = ValidationRule.builder()
                    .fieldName(request.fieldName())
                    .ruleType(request.ruleType())
                    .pattern(request.pattern())
                    .description(request.description())
                    .active(request.active())
                    .global(false)       // private to this pipeline
                    .pipeline(pipeline)
                    .build();

            log.info("Creating private rule fieldName={}, ruleType={} on pipeline id={}",
                    request.fieldName(), request.ruleType(), pipelineId);
        }

        // 1 — Sauvegarde de la règle de validation
        ValidationRule saved = validationRuleRepository.save(rule);

        // 2 — Mutation du statut du pipeline si celui-ci est en brouillon (DRAFT)
        if (pipeline.getStatus() == PipelineStatus.DRAFT) {
            pipeline.setStatus(PipelineStatus.CONFIGURED);
            pipelineRepository.save(pipeline);
            log.info("Pipeline id={} status updated to CONFIGURED", pipelineId);
        }

        return toResponse(saved, pipelineId);
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<PipelineValidationRuleResponse> getRules(Long pipelineId) {
        findPipelineOrThrow(pipelineId);
        return validationRuleRepository.findAllByPipelineId(pipelineId)
                .stream()
                .map(r -> toResponse(r, pipelineId))
                .toList();
    }

    // ── UPDATE (private rules only) ───────────────────────────────────────────

    @Override
    @Transactional
    public PipelineValidationRuleResponse updateRule(Long pipelineId, Long ruleId,
                                                     PipelineValidationRuleRequest request) {
        ValidationRule rule = findRuleOrThrow(pipelineId, ruleId);

        if (rule.isGlobal()) {
            throw new IllegalArgumentException(
                    "Global rules cannot be edited here. Detach and re-create as a private rule if customisation is needed.");
        }

        // Check duplicate only if fieldName or ruleType changes
        boolean fieldChanged = !rule.getFieldName().equals(request.fieldName())
                || rule.getRuleType() != request.ruleType();
        if (fieldChanged && validationRuleRepository.existsByPipelineIdAndFieldNameAndRuleType(
                pipelineId, request.fieldName(), request.ruleType())) {
            throw new IllegalArgumentException(
                    "A rule of type " + request.ruleType()
                            + " already exists for field '" + request.fieldName() + "' on this pipeline");
        }

        rule.setFieldName(request.fieldName());
        rule.setRuleType(request.ruleType());
        rule.setPattern(request.pattern());
        rule.setDescription(request.description());


        ValidationRule updated = validationRuleRepository.save(rule);
        log.info("Updated private rule id={} on pipeline id={}", ruleId, pipelineId);
        return toResponse(updated, pipelineId);
    }

    // ── TOGGLE ACTIVE ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PipelineValidationRuleResponse toggleActive(Long pipelineId, Long ruleId) {
        ValidationRule rule = findRuleOrThrow(pipelineId, ruleId);
        rule.setActive(!rule.isActive());
        ValidationRule saved = validationRuleRepository.save(rule);
        log.info("Toggled rule id={} pipeline id={} active={}", ruleId, pipelineId, saved.isActive());
        return toResponse(saved, pipelineId);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteRule(Long pipelineId, Long ruleId) {
        ValidationRule rule = findRuleOrThrow(pipelineId, ruleId);
        validationRuleRepository.delete(rule);
        log.info("Deleted rule id={} from pipeline id={}", ruleId, pipelineId);
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private Pipeline findPipelineOrThrow(Long pipelineId) {
        return pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));
    }

    private ValidationRule findRuleOrThrow(Long pipelineId, Long ruleId) {
        ValidationRule rule = validationRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Validation rule not found with id=" + ruleId));
        if (rule.getPipeline() == null || !rule.getPipeline().getId().equals(pipelineId)) {
            throw new ResourceNotFoundException(
                    "Rule id=" + ruleId + " does not belong to pipeline id=" + pipelineId);
        }
        return rule;
    }

    private PipelineValidationRuleResponse toResponse(ValidationRule r, Long pipelineId) {
        return new PipelineValidationRuleResponse(
                r.getId(),
                r.getFieldName(),
                r.getRuleType(),
                r.getPattern(),
                r.getDescription(),
                r.isActive(),
                r.isGlobal(),
                pipelineId
        );
    }
}