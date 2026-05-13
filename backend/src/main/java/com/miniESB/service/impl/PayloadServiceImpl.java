package com.miniESB.service.impl;

import com.miniESB.domain.entity.Payload;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.PipelineField;
import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.PayloadStatus;
import com.miniESB.dto.payload.PayloadRequest;
import com.miniESB.dto.payload.PayloadResponse;
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

    private final PayloadRepository payloadRepository;
    private final PipelineRepository pipelineRepository;
    private final PipelineFieldRepository pipelineFieldRepository;
    private final StructuralValidatorService structuralValidatorService;

    @Override
    @Transactional
    public PayloadResponse receivePayload(Long pipelineId, PayloadRequest request) {
        // 1 — pipeline existe ?
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));

        // 2 — charger les PipelineField de cette pipeline
        List<PipelineField> fields = pipelineFieldRepository.findAllByPipelineId(pipelineId);

        // 3 — validation structurelle (lance PayloadValidationException si violations)
        if (!fields.isEmpty()) {
            structuralValidatorService.validate(request.rawContent(), fields);
        } else {
            log.warn("Pipeline id={} has no PipelineField defined — skipping structural validation", pipelineId);
        }

        // 4 — tout est OK on sauvegarde
        Payload payload = Payload.builder()
                .rawContent(request.rawContent())
                .format(DataFormat.valueOf(request.format().toUpperCase()))
                .receivedAt(LocalDateTime.now())
                .status(PayloadStatus.RECEIVED)
                .pipeline(pipeline)
                .build();

        Payload saved = payloadRepository.save(payload);
        log.info("Payload saved: id={}, pipeline={}, status={}", saved.getId(), pipelineId, saved.getStatus());
        return toResponse(saved);
    }

    @Override
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
    public PayloadResponse getPayloadById(Long id) {
        Payload payload = payloadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payload not found with id=" + id));
        return toResponse(payload);
    }

    // -------------------------------------------------------------------------
    // HELPER
    // -------------------------------------------------------------------------

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