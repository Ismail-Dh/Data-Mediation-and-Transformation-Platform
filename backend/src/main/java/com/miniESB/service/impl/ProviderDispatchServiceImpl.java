package com.miniESB.service.impl;

import com.miniESB.domain.entity.*;
import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.HttpRequestMethod;
import com.miniESB.domain.enums.PayloadStatus;
import com.miniESB.dto.dispatch.ProviderDispatchResult;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.*;
import com.miniESB.service.ProviderDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * T5 — Dispatche le payload mappé vers TOUS les providers du pipeline
 * (association {@link PipelineProvider} depuis V23/V24) et persiste une
 * {@link ProviderResponse} par provider pour traçabilité complète.
 * Chaque provider est appelé avec la méthode HTTP (GET/POST/PUT/PATCH)
 * choisie pour CE pipeline (portée par PipelineProvider.httpMethod).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderDispatchServiceImpl implements ProviderDispatchService {

    private final PipelineRepository         pipelineRepository;
    private final PayloadRepository          payloadRepository;
    private final ProviderResponseRepository providerResponseRepository;
    private final ExchangeLogRepository      exchangeLogRepository;
    private final RestTemplateBuilder        restTemplateBuilder;

    @Override
    @Transactional
    public List<ProviderDispatchResult> dispatch(Long pipelineId,
                                                 Long payloadId,
                                                 String mappedPayload) {

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

    // ── Appel HTTP vers un provider ──────────────────────────────────────────

    private ProviderDispatchResult callProvider(PipelineProvider link,
                                                Pipeline pipeline,
                                                Payload payload,
                                                String mappedPayload) {
        Provider provider = link.getProvider();
        String endpoint = provider.getEndpoint();
        HttpMethod method = resolveHttpMethod(link);
        long startMs = System.currentTimeMillis();

        log.info("{} {} → provider='{}' payload={}", method, endpoint, provider.getName(), payload.getId());

        try {
            RestTemplate rt = buildRestTemplate(provider.getTimeout());
            HttpEntity<String> req = new HttpEntity<>(mappedPayload, buildHeaders(pipeline));
            ResponseEntity<String> resp = rt.exchange(endpoint, method, req, String.class);

            long dur = System.currentTimeMillis() - startMs;
            int  st  = resp.getStatusCode().value();
            String body = resp.getBody();
            boolean ok = resp.getStatusCode().is2xxSuccessful();

            log.info("Provider '{}' → HTTP {} in {}ms", provider.getName(), st, dur);

            persistProviderResponse(payload, provider, st, body, ok, dur);
            persistExchangeLog(pipeline, st, body, dur, null);

            return ok
                    ? ProviderDispatchResult.ok(provider.getId(), provider.getName(), endpoint, st, body, dur)
                    : ProviderDispatchResult.httpError(provider.getId(), provider.getName(), endpoint, st, body, dur);

        } catch (HttpStatusCodeException ex) {
            long dur  = System.currentTimeMillis() - startMs;
            int  st   = ex.getStatusCode().value();
            String body = ex.getResponseBodyAsString();

            log.warn("Provider '{}' HTTP {} — {}", provider.getName(), st, body);
            persistProviderResponse(payload, provider, st, body, false, dur);
            persistExchangeLog(pipeline, st, body, dur, ex.getMessage());

            return ProviderDispatchResult.httpError(
                    provider.getId(), provider.getName(), endpoint, st, body, dur);

        } catch (ResourceAccessException ex) {
            long dur = System.currentTimeMillis() - startMs;
            String msg = "Network error → " + provider.getName() + ": " + ex.getMessage();

            log.error(msg);
            persistProviderResponse(payload, provider, 0, null, false, dur);
            persistExchangeLog(pipeline, 0, null, dur, msg);

            return ProviderDispatchResult.networkError(
                    provider.getId(), provider.getName(), endpoint, dur, msg);

        } catch (Exception ex) {
            long dur = System.currentTimeMillis() - startMs;
            String msg = "Unexpected error → " + provider.getName() + ": " + ex.getMessage();

            log.error(msg, ex);
            persistProviderResponse(payload, provider, 0, null, false, dur);
            persistExchangeLog(pipeline, 0, null, dur, msg);

            return ProviderDispatchResult.networkError(
                    provider.getId(), provider.getName(), endpoint, dur, msg);
        }
    }

    // ── Persistence T5 ───────────────────────────────────────────────────────

    /**
     * Persiste (ou met à jour) la ProviderResponse pour le couple (payload, provider).
     * Gère le re-dispatch : si une réponse existe déjà pour ce couple, elle est mise à jour.
     */
    private void persistProviderResponse(Payload payload, Provider provider,
                                         int httpStatus, String rawBody,
                                         boolean success, long durationMs) {
        ProviderResponse pr = providerResponseRepository
                .findByPayloadIdAndProviderId(payload.getId(), provider.getId())
                .orElseGet(() -> ProviderResponse.builder()
                        .payload(payload)
                        .provider(provider)
                        .build());

        pr.setHttpStatus(httpStatus);
        pr.setRawContent(rawBody != null ? rawBody : "");
        pr.setSuccess(success);
        pr.setReceivedAt(LocalDateTime.now());
        pr.setDurationMs(durationMs);
        providerResponseRepository.save(pr);
    }

    private void persistExchangeLog(Pipeline pipeline, int httpStatus,
                                    String message, long durationMs, String errorDetail) {
        ExchangeLog log = ExchangeLog.builder()
                .pipeline(pipeline)
                .timestamp(LocalDateTime.now())
                .httpStatus(httpStatus)
                .message(message != null ? truncate(message, 4000) : "")
                .errorDetail(errorDetail)
                .duration(durationMs)
                .build();
        exchangeLogRepository.save(log);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Résout la méthode HTTP à utiliser pour l'appel sortant vers ce provider,
     * dans le contexte de CE pipeline. La méthode est portée par l'association
     * PipelineProvider (choisie à la création/édition du pipeline), et non par
     * le provider lui-même — un même provider peut donc être appelé différemment
     * selon le pipeline. Fallback défensif sur POST si non renseignée.
     */
    private HttpMethod resolveHttpMethod(PipelineProvider link) {
        HttpRequestMethod configured = link.getHttpMethod();
        if (configured == null) {
            log.warn("Pipeline id={} → provider '{}' has no httpMethod configured — defaulting to POST",
                    link.getPipeline().getId(), link.getProvider().getName());
            return HttpMethod.POST;
        }
        return switch (configured) {
            case GET   -> HttpMethod.GET;
            case POST  -> HttpMethod.POST;
            case PUT   -> HttpMethod.PUT;
            case PATCH -> HttpMethod.PATCH;
        };
    }

    private RestTemplate buildRestTemplate(int timeoutSec) {
        Duration t = Duration.ofSeconds(timeoutSec > 0 ? timeoutSec : 30);
        return restTemplateBuilder.connectTimeout(t).readTimeout(t).build();
    }

    private HttpHeaders buildHeaders(Pipeline pipeline) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(pipeline.getOutputFormat() == DataFormat.XML
                ? MediaType.APPLICATION_XML : MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.ALL));
        return h;
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}