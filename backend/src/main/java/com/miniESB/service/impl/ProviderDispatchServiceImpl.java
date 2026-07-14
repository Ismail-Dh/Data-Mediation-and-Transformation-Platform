package com.miniESB.service.impl;

import com.miniESB.domain.entity.*;
import com.miniESB.domain.enums.HttpRequestMethod;
import com.miniESB.domain.enums.PayloadStatus;
import com.miniESB.dto.dispatch.ProviderDispatchResult;
import com.miniESB.engine.dispatch.DispatchOutcome;
import com.miniESB.engine.dispatch.HttpDispatchExecutor;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.*;
import com.miniESB.service.ProviderDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * T5 — Dispatche le payload mappé vers TOUS les providers du pipeline
 * (association {@link PipelineProvider}) et persiste une {@link ProviderResponse}
 * par provider pour traçabilité complète. Chaque provider est appelé avec la
 * méthode HTTP (GET/POST/PUT/PATCH) choisie pour CE pipeline (portée par
 * {@code PipelineProvider.httpMethod}).
 *
 * <p><strong>Depuis le refactoring</strong>, l'appel HTTP lui-même (construction
 * du {@code RestTemplate}, des en-têtes, classification succès/erreur HTTP/erreur
 * réseau) est délégué à {@link HttpDispatchExecutor}, partagé avec
 * {@code EngineProcessService} (mode fichier), qui dupliquait auparavant ce même
 * code. Cette classe ne garde que ce qui lui est propre : résolution de la
 * méthode HTTP par pipeline, et persistance ({@link ProviderResponse}, {@link ExchangeLog}).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
public class ProviderDispatchServiceImpl implements ProviderDispatchService {

    private final PipelineRepository pipelineRepository;
    private final PayloadRepository payloadRepository;
    private final ProviderResponseRepository providerResponseRepository;
    private final ExchangeLogRepository exchangeLogRepository;
    private final HttpDispatchExecutor dispatchExecutor;

    @Override
    @Transactional
    public List<ProviderDispatchResult> dispatch(Long pipelineId, Long payloadId, String mappedPayload) {

        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found: " + pipelineId));

        Payload payload = payloadRepository.findById(payloadId)
                .orElseThrow(() -> new ResourceNotFoundException("Payload not found: " + payloadId));

        List<PipelineProvider> pipelineProviders = pipeline.getPipelineProviders();
        if (pipelineProviders == null || pipelineProviders.isEmpty()) {
            log.warn("Pipeline id={} has no provider attached — dispatch skipped", pipelineId);
            return List.of();
        }

        List<ProviderDispatchResult> results = new ArrayList<>();
        boolean anySuccess = false;

        for (PipelineProvider link : pipelineProviders) {
            ProviderDispatchResult result = callProvider(link, pipeline, payload, mappedPayload);
            results.add(result);
            if (result.success()) anySuccess = true;
        }

        payload.setStatus(anySuccess ? PayloadStatus.SENT : PayloadStatus.FAILED);
        payloadRepository.save(payload);

        log.info("Dispatch done — payload={} pipeline={} providers={} anySuccess={}",
                payloadId, pipelineId, results.size(), anySuccess);

        return results;
    }

    private ProviderDispatchResult callProvider(PipelineProvider link, Pipeline pipeline,
                                                 Payload payload, String mappedPayload) {
        Provider provider = link.getProvider();
        String endpoint = provider.getEndpoint();
        HttpMethod method = resolveHttpMethod(link);

        DispatchOutcome outcome = dispatchExecutor.call(
                endpoint, method, mappedPayload, pipeline.getOutputFormat(),
                provider.getTimeout(), provider.getName());

        persistProviderResponse(payload, provider, outcome);
        persistExchangeLog(pipeline, outcome);

        if (outcome.errorMessage() != null) {
            return ProviderDispatchResult.networkError(
                    provider.getId(), provider.getName(), endpoint, outcome.durationMs(), outcome.errorMessage());
        }
        return outcome.success()
                ? ProviderDispatchResult.ok(provider.getId(), provider.getName(), endpoint,
                        outcome.httpStatus(), outcome.rawBody(), outcome.durationMs())
                : ProviderDispatchResult.httpError(provider.getId(), provider.getName(), endpoint,
                        outcome.httpStatus(), outcome.rawBody(), outcome.durationMs());
    }

    // ── Persistence T5 ───────────────────────────────────────────────────────

    /**
     * Persiste (ou met à jour) la ProviderResponse pour le couple (payload, provider).
     * Gère le re-dispatch : si une réponse existe déjà pour ce couple, elle est mise à jour.
     */
    private void persistProviderResponse(Payload payload, Provider provider, DispatchOutcome outcome) {
        ProviderResponse pr = providerResponseRepository
                .findByPayloadIdAndProviderId(payload.getId(), provider.getId())
                .orElseGet(() -> ProviderResponse.builder()
                        .payload(payload)
                        .provider(provider)
                        .build());

        pr.setHttpStatus(outcome.httpStatus());
        pr.setRawContent(outcome.rawBody() != null ? outcome.rawBody() : "");
        pr.setSuccess(outcome.success());
        pr.setReceivedAt(LocalDateTime.now());
        pr.setDurationMs(outcome.durationMs());
        providerResponseRepository.save(pr);
    }

    private void persistExchangeLog(Pipeline pipeline, DispatchOutcome outcome) {
        String message = outcome.errorMessage() != null ? outcome.errorMessage() : outcome.rawBody();
        ExchangeLog entry = ExchangeLog.builder()
                .pipeline(pipeline)
                .timestamp(LocalDateTime.now())
                .httpStatus(outcome.httpStatus())
                .message(message != null ? truncate(message, 4000) : "")
                .errorDetail(outcome.errorMessage())
                .duration(outcome.durationMs())
                .build();
        exchangeLogRepository.save(entry);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Résout la méthode HTTP à utiliser pour l'appel sortant vers ce provider,
     * dans le contexte de CE pipeline. La méthode est portée par l'association
     * PipelineProvider (choisie à la création/édition du pipeline), et non par
     * le provider lui-même. Fallback défensif sur POST si non renseignée.
     */
    private HttpMethod resolveHttpMethod(PipelineProvider link) {
        HttpRequestMethod configured = link.getHttpMethod();
        if (configured == null) {
            log.warn("Pipeline id={} → provider '{}' has no httpMethod configured — defaulting to POST",
                    link.getPipeline().getId(), link.getProvider().getName());
            return HttpMethod.POST;
        }
        return switch (configured) {
            case GET -> HttpMethod.GET;
            case POST -> HttpMethod.POST;
            case PUT -> HttpMethod.PUT;
            case PATCH -> HttpMethod.PATCH;
        };
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
