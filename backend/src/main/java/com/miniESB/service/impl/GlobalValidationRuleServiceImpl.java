package com.miniESB.service.impl;



import com.miniESB.domain.entity.ValidationRule;
import com.miniESB.dto.globalValidationRule.GlobalValidationRuleRequestDTO;
import com.miniESB.dto.globalValidationRule.GlobalValidationRuleResponseDTO;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.exception.RuleInUseException;
import com.miniESB.repository.ValidationRuleRepository;
import com.miniESB.service.GlobalValidationRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalValidationRuleServiceImpl implements GlobalValidationRuleService {

    private final ValidationRuleRepository validationRuleRepository;

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public GlobalValidationRuleResponseDTO createRule(GlobalValidationRuleRequestDTO request) {
        log.info("Creating global validation rule: fieldName={}, ruleType={}", request.fieldName(), request.ruleType());

        ValidationRule rule = ValidationRule.builder()
                .fieldName(request.fieldName())
                .ruleType(request.ruleType())
                .pattern(request.pattern())
                .active(request.active())
                .global(true)          // Admin-created rules are always global
                .pipeline(null)        // Global rules are not bound to a pipeline
                .build();

        ValidationRule saved = validationRuleRepository.save(rule);
        log.info("Global validation rule created with id={}", saved.getId());
        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // READ
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<GlobalValidationRuleResponseDTO> getAllRules() {
        log.debug("Fetching all global validation rules (Admin view)");
        return validationRuleRepository.findAllByGlobalTrue()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GlobalValidationRuleResponseDTO> getActiveRules() {
        log.debug("Fetching active global validation rules (Developer view)");
        return validationRuleRepository.findAllByGlobalTrueAndActiveTrue()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GlobalValidationRuleResponseDTO getRuleById(Long id) {
        log.debug("Fetching global validation rule id={}", id);
        return toResponse(findGlobalRuleOrThrow(id));
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public GlobalValidationRuleResponseDTO updateRule(Long id, GlobalValidationRuleRequestDTO request) {
        log.info("Updating global validation rule id={}", id);

        ValidationRule rule = findGlobalRuleOrThrow(id);
        rule.setFieldName(request.fieldName());
        rule.setRuleType(request.ruleType());
        rule.setPattern(request.pattern());
        rule.setActive(request.active());
        // global flag stays true — never changed via this method

        ValidationRule updated = validationRuleRepository.save(rule);
        log.info("Global validation rule id={} updated", id);
        return toResponse(updated);
    }

    // -------------------------------------------------------------------------
    // SOFT-DELETE  (deactivate)
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void deactivateRule(Long id) {
        log.info("Deactivating global validation rule id={}", id);
        ValidationRule rule = findGlobalRuleOrThrow(id);
        rule.setActive(false);
        validationRuleRepository.save(rule);
        log.info("Global validation rule id={} deactivated (active=false)", id);
    }

    // -------------------------------------------------------------------------
    // HARD-DELETE
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void deleteRule(Long id) {
        log.info("Attempting to delete global validation rule id={}", id);

        // 1 — rule must exist
        findGlobalRuleOrThrow(id);

        // 2 — reject if rule is referenced by a pipeline (409)
        if (validationRuleRepository.isGlobalRuleUsedByPipeline(id)) {
            log.warn("Cannot delete global rule id={}: still used by a pipeline", id);
            throw new RuleInUseException(id);
        }

        validationRuleRepository.deleteById(id);
        log.info("Global validation rule id={} deleted", id);
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    /**
     * Finds a global ValidationRule by id or throws ResourceNotFoundException.
     */
    private ValidationRule findGlobalRuleOrThrow(Long id) {
        return validationRuleRepository.findById(id)
                .filter(ValidationRule::isGlobal)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Global validation rule not found with id=" + id));
    }

    /**
     * Maps a ValidationRule entity to its response DTO.
     */
    private GlobalValidationRuleResponseDTO toResponse(ValidationRule rule) {
        return new GlobalValidationRuleResponseDTO(
                rule.getId(),
                rule.getFieldName(),
                rule.getRuleType(),
                rule.getPattern(),
                rule.isActive(),
                rule.isGlobal()
        );
    }
}
