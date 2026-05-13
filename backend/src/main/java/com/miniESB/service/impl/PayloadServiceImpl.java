package com.miniESB.service.impl;

import com.miniESB.domain.entity.Payload;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.PipelineField;
import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.PayloadStatus;
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

    private final PayloadRepository          payloadRepository;
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

        // 2 — charger le schéma (PipelineField) défini par le Developer
        List<PipelineField> fields = pipelineFieldRepository.findAllByPipelineId(pipelineId);

        // 3 — validation structurelle niveau 1
        //     Si aucun champ défini → payload accepté sans contrôle structurel
        if (fields.isEmpty()) {
            log.warn("Pipeline id={} has no PipelineField defined — skipping structural validation", pipelineId);
        } else {
            /*
             * structuralValidatorService.validate() lève PayloadValidationException
             * si au moins une violation est détectée.
             * Le GlobalExceptionHandler la traduit en HTTP 422 avec le détail des violations.
             * On persiste quand même le payload avec status=FAILED pour traçabilité.
             */
            try {
                structuralValidatorService.validate(request.rawContent(), fields);
            } catch (PayloadValidationException pve) {
                // Persist failed payload for auditability, then re-throw for 422
                persistPayload(request, pipeline, PayloadStatus.FAILED);
                log.warn("Structural validation FAILED for pipeline={} — {} violation(s)",
                        pipelineId, pve.getViolations().size());
                throw pve;
            }
        }

        // 4 — validation OK (ou pas de schéma) → statut VALIDATED
        Payload saved = persistPayload(request, pipeline, PayloadStatus.VALIDATED);
        log.info("Payload accepted: id={}, pipeline={}, status={}", saved.getId(), pipelineId, saved.getStatus());
        return toResponse(saved);
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