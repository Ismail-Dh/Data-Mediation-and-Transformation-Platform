package com.miniESB.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.*;
import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.PayloadStatus;
import com.miniESB.dto.mapping.MappingResultResponse;
import com.miniESB.dto.sandbox.MappingRuleSummary;
import com.miniESB.dto.sandbox.SandboxRequest;
import com.miniESB.dto.sandbox.SandboxResponse;
import com.miniESB.exception.PayloadValidationException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.*;
import com.miniESB.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@Service
@RequiredArgsConstructor
public class SandboxServiceImpl implements SandboxService {

    private final PipelineRepository         pipelineRepository;
    private final PayloadRepository          payloadRepository;
    private final PipelineFieldRepository    pipelineFieldRepository;
    private final ValidationRuleRepository   validationRuleRepository;
    private final MappingRuleRepository      mappingRuleRepository;
    private final StructuralValidatorService structuralValidatorService;
    private final BusinessValidatorService   businessValidatorService;
    private final MappingService             mappingService;
    private final SandboxLogRepository       sandboxLogRepository;
    private final ObjectMapper               objectMapper;

    @Override
    @Transactional
    public SandboxResponse run(Long pipelineId, SandboxRequest request) {
        long start = System.currentTimeMillis();

        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));

        Payload payload = payloadRepository.save(Payload.builder()
                .rawContent(request.rawContent())
                .format(DataFormat.valueOf(request.format().toUpperCase()))
                .receivedAt(LocalDateTime.now())
                .status(PayloadStatus.RECEIVED)
                .pipeline(pipeline)
                .build());

        List<PipelineField>  fields = pipelineFieldRepository.findAllByPipelineId(pipelineId);
        List<ValidationRule> rules  = validationRuleRepository.findAllByPipelineIdAndActiveTrue(pipelineId);
        String validationMessage = "Validation passed";

        // ── Niveau 1 — Structural ─────────────────────────────────────────
        if (fields.isEmpty()) {
            validationMessage = "No schema defined — structural validation skipped";
        } else {
            try {
                structuralValidatorService.validate(request.rawContent(), fields);
            } catch (PayloadValidationException pve) {
                payload.setStatus(PayloadStatus.FAILED);
                payloadRepository.save(payload);
                SandboxResponse response = failResponse(pipeline, payload, pve,
                        buildMappingSummaryNotReached(pipelineId));
                saveLogAsync(pipeline, payload, response,
                        System.currentTimeMillis() - start,
                        request.format(), request.rawContent(), "STRUCTURAL");
                return response;
            }
        }

        // ── Niveau 2 — Rules ──────────────────────────────────────────────
        if (!rules.isEmpty()) {
            try {
                businessValidatorService.validate(request.rawContent(), rules);
            } catch (PayloadValidationException pve) {
                payload.setStatus(PayloadStatus.FAILED);
                payloadRepository.save(payload);
                SandboxResponse response = failResponse(pipeline, payload, pve,
                        buildMappingSummaryNotReached(pipelineId));
                saveLogAsync(pipeline, payload, response,
                        System.currentTimeMillis() - start,
                        request.format(), request.rawContent(), "RULES");
                return response;
            }
        }

        // ── Mapping ───────────────────────────────────────────────────────
        payload.setStatus(PayloadStatus.VALIDATED);
        payloadRepository.save(payload);

        MappingResultResponse mappingResult =
                mappingService.applyMappingToPayload(pipelineId, payload.getId());

        List<MappingRuleSummary> mappingSummary =
                buildMappingSummary(pipelineId, mappingResult.original(), mappingResult.mapped());

        SandboxResponse response = new SandboxResponse(
                pipelineId, payload.getId(),
                true, validationMessage,
                true, mappingResult.original(), mappingResult.mapped(),
                PayloadStatus.MAPPED, pipeline.getStatus(),
                List.of(), mappingSummary
        );

        saveLogAsync(pipeline, payload, response,
                System.currentTimeMillis() - start,
                request.format(), request.rawContent(), null);
        return response;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SandboxResponse failResponse(Pipeline pipeline, Payload payload,
                                          PayloadValidationException pve,
                                          List<MappingRuleSummary> mappingSummary) {
        return new SandboxResponse(
                pipeline.getId(), payload.getId(),
                false, buildViolationMessage(pve),
                false, null, null,
                PayloadStatus.FAILED, pipeline.getStatus(),
                pve.getViolations(), mappingSummary
        );
    }

    private List<MappingRuleSummary> buildMappingSummary(Long pipelineId,
                                                          Map<String, Object> original,
                                                          Map<String, Object> mapped) {
        return mappingRuleRepository.findByPipelineIdAndActiveTrue(pipelineId)
                .stream()
                .map(r -> new MappingRuleSummary(
                        r.getMappingType().name(),
                        r.getSourceField(),
                        r.getTargetField(),
                        r.getExpression(),
                        true,
                        getValueAsString(original, r.getSourceField()),
                        getValueAsString(mapped,   r.getTargetField())
                )).toList();
    }

    private List<MappingRuleSummary> buildMappingSummaryNotReached(Long pipelineId) {
        return mappingRuleRepository.findByPipelineIdAndActiveTrue(pipelineId)
                .stream()
                .map(r -> new MappingRuleSummary(
                        r.getMappingType().name(),
                        r.getSourceField(),
                        r.getTargetField(),
                        r.getExpression(),
                        false, null, null
                )).toList();
    }

    private String getValueAsString(Map<String, Object> map, String field) {
        if (map == null || field == null) return null;
        Object val = map.get(field);
        if (val == null) return null;
        return val instanceof String s ? "\"" + s + "\"" : val.toString();
    }

    @Async
    protected void saveLogAsync(Pipeline pipeline, Payload payload,
                                 SandboxResponse response, long durationMs,
                                 String format, String rawContent, String failureStep) {
        try {
            sandboxLogRepository.save(SandboxLog.builder()
                    .pipeline(pipeline)
                    .payload(payload)
                    .validationPassed(response.validationPassed())
                    .mappingApplied(response.mappingApplied())
                    .validationMessage(response.validationMessage())
                    .durationMs(durationMs)
                    .executedAt(LocalDateTime.now())
                    .inputFormat(format)
                    .rawContent(rawContent)
                    .failureStep(failureStep)
                    .violations(response.violations() != null
                            ? objectMapper.writeValueAsString(response.violations()) : null)
                    .originalPayload(response.originalPayload() != null
                            ? objectMapper.writeValueAsString(response.originalPayload()) : null)
                    .mappedPayload(response.mappedPayload() != null
                            ? objectMapper.writeValueAsString(response.mappedPayload()) : null)
                    .mappingSummary(response.mappingSummary() != null
                            ? objectMapper.writeValueAsString(response.mappingSummary()) : null)
                    .build());
        } catch (Exception e) {
            log.error("Failed to save sandbox log: {}", e.getMessage());
        }
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