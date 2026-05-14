package com.miniESB.service.impl;

import com.miniESB.domain.entity.PipelineField;
import com.miniESB.domain.entity.ValidationRule;
import com.miniESB.dto.validation.ValidationPreviewRequest;
import com.miniESB.dto.validation.ValidationPreviewResponse;
import com.miniESB.exception.FieldViolation;
import com.miniESB.exception.PayloadValidationException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.PipelineFieldRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.repository.ValidationRuleRepository;
import com.miniESB.service.BusinessValidatorService;
import com.miniESB.service.StructuralValidatorService;
import com.miniESB.service.ValidationPreviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationPreviewServiceImpl implements ValidationPreviewService {

    private final PipelineRepository      pipelineRepository;
    private final PipelineFieldRepository pipelineFieldRepository;
    private final ValidationRuleRepository validationRuleRepository;
    private final StructuralValidatorService structuralValidatorService;
    private final BusinessValidatorService   businessValidatorService;

    @Override
    @Transactional(readOnly = true)
    public ValidationPreviewResponse preview(Long pipelineId, ValidationPreviewRequest request) {

        // 1 — pipeline must exist
        pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));

        // 2 — load schema fields & active rules
        List<PipelineField>    fields = pipelineFieldRepository.findAllByPipelineId(pipelineId);
        List<ValidationRule>   rules  = validationRuleRepository.findAllByPipelineIdAndActiveTrue(pipelineId);

        // 3 — no schema AND no rules → trivially valid
        if (fields.isEmpty() && rules.isEmpty()) {
            log.debug("Preview pipeline={}: no schema and no rules", pipelineId);
            return new ValidationPreviewResponse(true, true, true, 0, 0, 0, List.of());
        }

        // ── Niveau 1 ──────────────────────────────────────────────────────────
        boolean structuralOk = true;
        List<FieldViolation> violations = List.of();

        if (!fields.isEmpty()) {
            try {
                structuralValidatorService.validate(request.rawContent(), fields);
            } catch (PayloadValidationException pve) {
                structuralOk = false;
                violations   = pve.getViolations();
                log.debug("Preview niveau-1 FAILED pipeline={} — {} violation(s)", pipelineId, violations.size());
                // Stop here: no point running niveau-2 if structure is broken
                return new ValidationPreviewResponse(
                        false, false, false,
                        fields.size(), rules.size(),
                        violations.size(), violations);
            }
        }

        // ── Niveau 2 ──────────────────────────────────────────────────────────
        boolean businessOk = true;

        if (!rules.isEmpty()) {
            try {
                businessValidatorService.validate(request.rawContent(), rules);
            } catch (PayloadValidationException pve) {
                businessOk = false;
                violations = pve.getViolations();
                log.debug("Preview niveau-2 FAILED pipeline={} — {} violation(s)", pipelineId, violations.size());
                return new ValidationPreviewResponse(
                        false, true, false,
                        fields.size(), rules.size(),
                        violations.size(), violations);
            }
        }

        // ── All good ──────────────────────────────────────────────────────────
        log.debug("Preview PASSED pipeline={} (fields={}, rules={})", pipelineId, fields.size(), rules.size());
        return new ValidationPreviewResponse(true, true, true, fields.size(), rules.size(), 0, List.of());
    }
}