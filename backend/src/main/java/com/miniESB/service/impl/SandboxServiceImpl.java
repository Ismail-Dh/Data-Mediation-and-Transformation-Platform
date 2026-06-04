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
    private final StructuralValidatorService structuralValidatorService;
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

        // 3 — validation structurelle
        List<PipelineField> fields = pipelineFieldRepository.findAllByPipelineId(pipelineId);
        String validationMessage = "Validation passed";

        if (fields.isEmpty()) {
            validationMessage = "No schema defined — validation skipped";
            log.warn("Pipeline id={} has no PipelineField defined — skipping validation", pipelineId);
        } else {
            try {
                structuralValidatorService.validate(request.rawContent(), fields);
            } catch (PayloadValidationException pve) {
                // payload → FAILED
                payload.setStatus(PayloadStatus.FAILED);
                payloadRepository.save(payload);
                log.warn("Sandbox validation FAILED for pipeline={}", pipelineId);

                return new SandboxResponse(
                        pipelineId,
                        payload.getId(),
                        false,
                        buildViolationMessage(pve),
                        false,
                        null,
                        null,
                        PayloadStatus.FAILED,
                        pipeline.getStatus() // pipeline status unchanged
                );
            }
        }

        // 4 — validation OK → payload VALIDATED
        payload.setStatus(PayloadStatus.VALIDATED);
        payloadRepository.save(payload);
        log.info("Sandbox validation passed: payload={}", payload.getId());

        // 5 — mapping → payload MAPPED
        MappingResultResponse mappingResult =
                mappingService.applyMappingToPayload(pipelineId, payload.getId());
        log.info("Sandbox mapping applied: payload={}", payload.getId());

        // 6 — pipeline status inchangé — c'est l'user qui valide manuellement
        return new SandboxResponse(
                pipelineId,
                payload.getId(),
                true,
                validationMessage,
                true,
                mappingResult.original(),
                mappingResult.mapped(),
                PayloadStatus.MAPPED,
                pipeline.getStatus() // retourne le statut actuel sans le modifier
        );
    }

    // -------------------------------------------------------------------------
    // HELPER
    // -------------------------------------------------------------------------

    private String buildViolationMessage(PayloadValidationException pve) {
        StringBuilder sb = new StringBuilder("Validation failed — ");
        sb.append(pve.getViolations().size()).append(" violation(s): ");
        pve.getViolations().forEach(v ->
                sb.append("[").append(v.fieldPath())
                  .append(": ").append(v.message()).append("] "));
        return sb.toString().trim();
    }
}