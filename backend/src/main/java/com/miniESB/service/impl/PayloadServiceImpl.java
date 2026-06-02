package com.miniESB.service.impl;

import com.miniESB.domain.entity.Payload;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.PipelineField;
import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.PayloadStatus;
import com.miniESB.domain.enums.PipelineStatus; // Ajout potentiel selon votre package d'enums
import com.miniESB.dto.payload.PayloadRequest;
import com.miniESB.dto.payload.PayloadResponse;
import com.miniESB.exception.PayloadValidationException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.PayloadRepository;
import com.miniESB.repository.PipelineFieldRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.service.PayloadService;
import com.miniESB.service.StructuralValidatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayloadServiceImpl implements PayloadService {

    private final PayloadRepository         payloadRepository;
    private final PipelineRepository         pipelineRepository;
    private final PipelineFieldRepository    pipelineFieldRepository;
    private final StructuralValidatorService structuralValidatorService;

    // -------------------------------------------------------------------------
    // SUBMIT — validation structurelle (niveau 1) + persistence
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public PayloadResponse receivePayload(Long pipelineId, PayloadRequest request) {

        // 1 — pipeline existe ?
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));

        // 2 — sauvegarder en RECEIVED immédiatement pour traçabilité
        Payload payload = persistPayload(request, pipeline, PayloadStatus.RECEIVED);
        log.info("Payload received: id={}, pipeline={}", payload.getId(), pipelineId);

        // 3 — charger le schéma (PipelineField) défini par le Developer
        List<PipelineField> fields = pipelineFieldRepository.findAllByPipelineId(pipelineId);

        // 4 — validation structurelle niveau 1
        if (fields.isEmpty()) {
            log.warn("Pipeline id={} has no PipelineField defined — skipping structural validation", pipelineId);
        } else {
            /*
             * structuralValidatorService.validate() lève PayloadValidationException
             * si au moins une violation est détectée.
             */
            try {
                structuralValidatorService.validate(request.rawContent(), fields);
            } catch (PayloadValidationException pve) {
                // payload → FAILED
                payload.setStatus(PayloadStatus.FAILED);
                payloadRepository.save(payload);
                
                log.warn("Structural validation FAILED for pipeline={} — {} violation(s)",
                        pipelineId, pve.getViolations().size());
                throw pve;
            }
        }

        // 5 — validation OK (ou pas de schéma) → statut VALIDATED
        payload.setStatus(PayloadStatus.VALIDATED);
        payloadRepository.save(payload);

        // 6 — pipeline → VALIDATED
        pipeline.setStatus(PipelineStatus.VALIDATED);
        pipelineRepository.save(pipeline);

        log.info("Payload validated: id={}, pipeline={}", payload.getId(), pipelineId);
        return toResponse(payload);
    }

    // -------------------------------------------------------------------------
    // READ
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<PayloadResponse> getPayloadsByPipeline(Long pipelineId) {
        pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));
        return payloadRepository.findAllByPipelineId(pipelineId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PayloadResponse getPayloadById(Long id) {
        Payload payload = payloadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payload not found with id=" + id));
        return toResponse(payload);
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private Payload persistPayload(PayloadRequest request, Pipeline pipeline, PayloadStatus status) {
        Payload payload = Payload.builder()
                .rawContent(request.rawContent())
                .format(DataFormat.valueOf(request.format().toUpperCase()))
                .receivedAt(LocalDateTime.now())
                .status(status)
                .pipeline(pipeline)
                .build();
        return payloadRepository.save(payload);
    }

    private PayloadResponse toResponse(Payload payload) {
        return new PayloadResponse(
                payload.getId(),
                payload.getRawContent(),
                payload.getFormat(),
                payload.getStatus(),
                payload.getReceivedAt(),
                payload.getPipeline().getId()
        );
    }
}