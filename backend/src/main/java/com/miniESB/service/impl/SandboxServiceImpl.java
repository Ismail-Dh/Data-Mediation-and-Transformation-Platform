package com.miniESB.service.impl;

import com.miniESB.domain.entity.Payload;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.PipelineField;
import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.PayloadStatus;
import com.miniESB.domain.enums.PipelineStatus;
import com.miniESB.dto.mapping.MappingResultResponse;
import com.miniESB.dto.sandbox.SandboxRequest;
import com.miniESB.dto.sandbox.SandboxResponse;
import com.miniESB.exception.PayloadValidationException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.PayloadRepository;
import com.miniESB.repository.PipelineFieldRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.service.MappingService;
import com.miniESB.service.SandboxService;
import com.miniESB.service.StructuralValidatorService;
import com.miniESB.repository.ValidationRuleRepository;
import com.miniESB.service.BusinessValidatorService;
import com.miniESB.domain.entity.ValidationRule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SandboxServiceImpl implements SandboxService {

    private final PipelineRepository         pipelineRepository;
    private final PayloadRepository          payloadRepository;
    private final PipelineFieldRepository    pipelineFieldRepository;
    private final ValidationRuleRepository   validationRuleRepository;   // ← ajout
    private final StructuralValidatorService structuralValidatorService;
    private final BusinessValidatorService   businessValidatorService;   // ← ajout
    private final MappingService             mappingService;

    @Override
    @Transactional
    public SandboxResponse run(Long pipelineId, SandboxRequest request) {

        // 1 — pipeline existe ?
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));

        // 2 — payload → RECEIVED
        Payload payload = payloadRepository.save(Payload.builder()
                .rawContent(request.rawContent())
                .format(DataFormat.valueOf(request.format().toUpperCase()))
                .receivedAt(LocalDateTime.now())
                .status(PayloadStatus.RECEIVED)
                .pipeline(pipeline)
                .build());
        log.info("Sandbox payload received: id={}, pipeline={}", payload.getId(), pipelineId);

        List<PipelineField>  fields = pipelineFieldRepository.findAllByPipelineId(pipelineId);
        List<ValidationRule> rules  = validationRuleRepository.findAllByPipelineIdAndActiveTrue(pipelineId);
        String validationMessage = "Validation passed";

        // 3a — validation structurelle (niveau 1)
        if (fields.isEmpty()) {
            validationMessage = "No schema defined — structural validation skipped";
            log.warn("Pipeline id={} has no PipelineField defined — skipping structural validation", pipelineId);
        } else {
            try {
                structuralValidatorService.validate(request.rawContent(), fields);
            } catch (PayloadValidationException pve) {
                payload.setStatus(PayloadStatus.FAILED);
                payloadRepository.save(payload);
                log.warn("Sandbox structural validation FAILED for pipeline={}", pipelineId);
                return failResponse(pipeline, payload, pve);
            }
        }

        // 3b — validation métier (niveau 2 : règles globales + privées actives)
        if (rules.isEmpty()) {
            log.debug("Pipeline id={} has no active rules — skipping business validation", pipelineId);
        } else {
            try {
                businessValidatorService.validate(request.rawContent(), rules);
            } catch (PayloadValidationException pve) {
                payload.setStatus(PayloadStatus.FAILED);
                payloadRepository.save(payload);
                log.warn("Sandbox business validation FAILED for pipeline={}", pipelineId);
                return failResponse(pipeline, payload, pve);
            }
        }

        // 4 — les deux niveaux OK → payload VALIDATED
        payload.setStatus(PayloadStatus.VALIDATED);
        payloadRepository.save(payload);
        log.info("Sandbox validation passed: payload={}", payload.getId());

        // 5 — mapping → payload MAPPED
        MappingResultResponse mappingResult =
                mappingService.applyMappingToPayload(pipelineId, payload.getId());
        log.info("Sandbox mapping applied: payload={}", payload.getId());

        // 6 — pipeline status inchangé
        return new SandboxResponse(
                pipelineId,
                payload.getId(),
                true,
                validationMessage,
                true,
                mappingResult.original(),
                mappingResult.mapped(),
                PayloadStatus.MAPPED,
                pipeline.getStatus()
        );
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private SandboxResponse failResponse(Pipeline pipeline, Payload payload,
                                         PayloadValidationException pve) {
        return new SandboxResponse(
                pipeline.getId(),
                payload.getId(),
                false,
                buildViolationMessage(pve),
                false,
                null,
                null,
                PayloadStatus.FAILED,
                pipeline.getStatus()
        );
    }

    private String buildViolationMessage(PayloadValidationException pve) {
        StringBuilder sb = new StringBuilder("Validation failed — ");
        sb.append(pve.getViolations().size()).append(" violation(s): ");
        pve.getViolations().forEach(v ->
                sb.append("[").append(v.fieldPath())
                  .append(": ").append(v.message()).append("] "));
        return sb.toString().trim();
    }
}