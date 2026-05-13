package com.miniESB.service.impl;

import com.miniESB.domain.entity.PipelineField;
import com.miniESB.dto.validation.ValidationPreviewRequest;
import com.miniESB.dto.validation.ValidationPreviewResponse;
import com.miniESB.exception.FieldViolation;
import com.miniESB.exception.PayloadValidationException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.PipelineFieldRepository;
import com.miniESB.repository.PipelineRepository;
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
    private final StructuralValidatorService structuralValidatorService;

    @Override
    @Transactional(readOnly = true)
    public ValidationPreviewResponse preview(Long pipelineId, ValidationPreviewRequest request) {

        // 1 — pipeline must exist
        pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));

        // 2 — load schema
        List<PipelineField> fields = pipelineFieldRepository.findAllByPipelineId(pipelineId);

        // 3 — no schema defined → trivially valid (nothing to check)
        if (fields.isEmpty()) {
            log.debug("Preview pipeline={}: no PipelineField defined, skipping structural check", pipelineId);
            return new ValidationPreviewResponse(true, true, 0, 0, List.of());
        }

        // 4 — run structural validation (niveau 1), catch violations without 422
        try {
            structuralValidatorService.validate(request.rawContent(), fields);
            // All good
            log.debug("Preview pipeline={}: structural validation PASSED ({} fields)", pipelineId, fields.size());
            return new ValidationPreviewResponse(true, true, fields.size(), 0, List.of());

        } catch (PayloadValidationException pve) {
            List<FieldViolation> violations = pve.getViolations();
            log.debug("Preview pipeline={}: structural validation FAILED — {} violation(s)", pipelineId, violations.size());
            return new ValidationPreviewResponse(false, false, fields.size(), violations.size(), violations);
        }
    }
}