package com.miniESB.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.ConsumerResponse;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.enums.DataFormat;
import com.miniESB.dto.dispatch.ProviderDispatchResult;
import com.miniESB.dto.payload.PayloadRequest;
import com.miniESB.dto.payload.PayloadResponse;
import com.miniESB.dto.process.ProcessRequest;
import com.miniESB.dto.process.ProcessResponse;
import com.miniESB.dto.response.ProviderResponseDetail;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.ConsumerResponseRepository;
import com.miniESB.repository.PayloadRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.service.MappingService;
import com.miniESB.service.PayloadService;
import com.miniESB.service.ProcessOrchestrationService;
import com.miniESB.service.ProviderDispatchService;
import com.miniESB.service.ResponseMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T7 — Implémentation du flux complet /api/process.
 *
 * <p>Ce service orchestre les 6 étapes sans contenir de logique métier propre :
 * chaque étape est déléguée au service spécialisé. Il est le seul point
 * d'entrée qui connaît toute la chaîne.</p>
 *
 * <p>En cas d'échec à l'étape validation ou mapping entrant, une exception
 * est levée immédiatement (fail-fast). En cas d'échec du dispatch ou de la
 * validation des réponses, le flux continue et l'échec est consigné dans
 * le {@link ProcessResponse} retourné au consommateur.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessOrchestrationServiceImpl implements ProcessOrchestrationService {

    private final PipelineRepository        pipelineRepository;
    private final PayloadRepository         payloadRepository;
    private final ConsumerResponseRepository consumerResponseRepository;
    private final PayloadService            payloadService;
    private final MappingService            mappingService;
    private final ProviderDispatchService   dispatchService;
    private final ResponseMappingService    responseMappingService;
    private final ObjectMapper              objectMapper;

    @Override
    @Transactional
    public ProcessResponse process(ProcessRequest request) {
        log.info("▶ /process — pipeline={} contentLength={}",
                request.pipelineId(), request.rawContent().length());

        // ── 1. Charger le pipeline ────────────────────────────────────────────
        Pipeline pipeline = pipelineRepository.findById(request.pipelineId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found: " + request.pipelineId()));

        // ── 2. Réception + validation structurelle (PayloadService) ───────────
        // receivePayload persiste le Payload et retourne un PayloadResponse DTO.
        // On charge ensuite l'entité Payload pour la persistance du ConsumerResponse.
        PayloadResponse payloadResponse = payloadService.receivePayload(
                request.pipelineId(),
                new PayloadRequest(request.rawContent(),
                        resolveFormat(request.inputFormat()).name()));

        var payload = payloadRepository.findById(payloadResponse.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payload not found after creation: " + payloadResponse.id()));

        log.info("Step 1-2 OK — payload={} status={}", payload.getId(), payload.getStatus());

        // ── 3. Mapping du payload entrant (MappingService) ───────────────────
        // applyMappingToPayload retourne le corps mappé en JSON string.
        // Note : dans le MappingServiceImpl revu, le dispatch N'est PLUS
        // appelé automatiquement — l'orchestrateur le contrôle ici.
        String mappedPayload = mappingService.applyAndReturnMapped(
                pipeline.getId(), payload.getId());

        log.info("Step 3 OK — mapped payload ready, length={}", mappedPayload.length());

        // ── 4. Dispatch HTTP vers tous les providers (ProviderDispatchService) ─
        List<ProviderDispatchResult> dispatchResults = dispatchService.dispatch(
                pipeline.getId(), payload.getId(), mappedPayload);

        log.info("Step 4 OK — {} provider(s) called", dispatchResults.size());

        // ── 5. Validation + mapping des réponses providers (ResponseMappingService) ─
        List<ProviderResponseDetail> details = responseMappingService.validateAndMap(
                pipeline.getId(), dispatchResults);

        log.info("Step 5 OK — {} response(s) validated", details.size());

        // ── 6. Agrégation + persistance ConsumerResponse ──────────────────────
        Map<String, Object> aggregated = aggregate(details);
        boolean overallSuccess = details.stream().anyMatch(ProviderResponseDetail::dispatchSuccess);
        int successCount = (int) details.stream().filter(ProviderResponseDetail::validationPassed).count();

        String aggregatedJson = toJson(aggregated);

        ConsumerResponse cr = ConsumerResponse.builder()
                .payload(payload)
                .pipeline(pipeline)
                .builtAt(LocalDateTime.now())
                .aggregatedBody(aggregatedJson)
                .overallSuccess(overallSuccess)
                .providerCount(details.size())
                .successCount(successCount)
                .build();
        consumerResponseRepository.save(cr);

        log.info("▶ /process complete — payload={} overall={} success={}/{}",
                payload.getId(), overallSuccess, successCount, details.size());

        return new ProcessResponse(
                payload.getId(),
                pipeline.getId(),
                pipeline.getName(),
                cr.getBuiltAt(),
                overallSuccess,
                details.size(),
                successCount,
                aggregated,
                details
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Agrège les corps mappés de tous les providers ayant réussi la validation.
     * En cas de clé dupliquée entre providers, la valeur du dernier provider
     * gagne (ordre de la liste = ordre de configuration du pipeline).
     * Les clés des providers suivants sont préfixées avec le nom du provider
     * si une collision est détectée, pour ne pas perdre d'information.
     */
    private Map<String, Object> aggregate(List<ProviderResponseDetail> details) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (ProviderResponseDetail detail : details) {
            if (!detail.validationPassed() || detail.mappedBody() == null) continue;

            detail.mappedBody().forEach((key, value) -> {
                if (result.containsKey(key)) {
                    // Collision : préfixer avec le nom du provider
                    String prefixedKey = detail.providerName() + "_" + key;
                    result.put(prefixedKey, value);
                } else {
                    result.put(key, value);
                }
            });
        }

        return result;
    }

    private DataFormat resolveFormat(String inputFormat) {
        if (inputFormat == null || inputFormat.isBlank()) return DataFormat.JSON;
        try {
            return DataFormat.valueOf(inputFormat.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DataFormat.JSON;
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize aggregated body", e);
            return "{}";
        }
    }
}