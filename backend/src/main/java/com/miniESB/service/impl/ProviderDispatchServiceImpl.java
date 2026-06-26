package com.miniESB.service.impl;

import com.miniESB.domain.entity.*;
import com.miniESB.domain.enums.PayloadStatus;
import com.miniESB.dto.dispatch.ProviderDispatchResult;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.*;
import com.miniESB.service.ProviderDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Envoie le payload mappé à chaque provider attaché au pipeline via HTTP POST.
 *
 * <p><b>Ce que fait ce service :</b></p>
 * <ol>
 *   <li>Charge le pipeline et récupère son provider (ou la liste si multi-provider à terme).</li>
 *   <li>Pour chaque provider, construit un RestTemplate avec le timeout configuré sur l'entité.</li>
 *   <li>Envoie le payload en POST avec Content-Type adapté au outputFormat du pipeline.</li>
 *   <li>Mesure la durée en millisecondes.</li>
 *   <li>Persiste la réponse dans {@code ProviderResponse} et trace dans {@code ExchangeLog}.</li>
 *   <li>Met à jour le statut du {@code Payload} (DISPATCHED ou FAILED).</li>
 *   <li>Retourne un {@code ProviderDispatchResult} par provider.</li>
 * </ol>
 *
 * <p>Les erreurs réseau (timeout, DNS, connexion refusée) sont capturées et
 * retournées comme résultat avec {@code httpStatus=0} — elles ne propagent
 * pas d'exception pour que les autres providers soient toujours appelés.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
public class ProviderDispatchServiceImpl implements ProviderDispatchService {

    private final PipelineRepository         pipelineRepository;
    private final PayloadRepository          payloadRepository;
    private final ProviderResponseRepository providerResponseRepository;
    private final ExchangeLogRepository      exchangeLogRepository;
    private final RestTemplateBuilder        restTemplateBuilder;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public List<ProviderDispatchResult> dispatch(Long pipelineId,
                                                 Long payloadId,
                                                 String mappedPayload) {

        // 1 — Charger le pipeline
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));

        // 2 — Charger le payload
        Payload payload = payloadRepository.findById(payloadId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payload not found with id=" + payloadId));

        // 3 — Collecter les providers attachés au pipeline
        //     Actuellement, un pipeline a un seul provider (@ManyToOne).
        //     On wrap dans une liste pour rester cohérent avec l'interface.
        List<Provider> providers = collectProviders(pipeline);

        if (providers.isEmpty()) {
            log.warn("Pipeline id={} has no provider attached — nothing dispatched", pipelineId);
            return List.of();
        }

        // 4 — Dispatcher vers chaque provider
        List<ProviderDispatchResult> results = new ArrayList<>();
        boolean anySuccess = false;

        for (Provider provider : providers) {
            ProviderDispatchResult result = callProvider(provider, pipeline, payload, mappedPayload);
            results.add(result);
            if (result.success()) anySuccess = true;
        }

        // 5 — Mettre à jour le statut du payload
        payload.setStatus(anySuccess ? PayloadStatus.SENT : PayloadStatus.FAILED);
        payloadRepository.save(payload);

        log.info("Dispatch complete for payload={}, pipeline={}: {} provider(s), anySuccess={}",
                payloadId, pipelineId, results.size(), anySuccess);

        return results;
    }

    // -------------------------------------------------------------------------
    // Dispatch vers un provider
    // -------------------------------------------------------------------------

    private ProviderDispatchResult callProvider(Provider provider,
                                                Pipeline pipeline,
                                                Payload payload,
                                                String mappedPayload) {

        String endpoint = provider.getEndpoint();
        long startMs = System.currentTimeMillis();

        log.info("Dispatching payload={} → provider='{}' ({})", payload.getId(), provider.getName(), endpoint);

        try {
            // RestTemplate avec timeout configuré par provider (en secondes → ms)
            RestTemplate restTemplate = buildRestTemplate(provider.getTimeout());

            // Construire la requête HTTP
            HttpHeaders headers = buildHeaders(pipeline);
            HttpEntity<String> request = new HttpEntity<>(mappedPayload, headers);

            // POST
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            long durationMs = System.currentTimeMillis() - startMs;
            int status = response.getStatusCode().value();
            String body = response.getBody();
            boolean success = response.getStatusCode().is2xxSuccessful();

            log.info("Provider '{}' responded: status={}, duration={}ms", provider.getName(), status, durationMs);

            // Persister réponse + log
            persistProviderResponse(payload, status, body, success);
            persistExchangeLog(pipeline, status, body, durationMs, null);

            return success
                    ? ProviderDispatchResult.ok(provider.getId(), provider.getName(), endpoint, status, body, durationMs)
                    : ProviderDispatchResult.httpError(provider.getId(), provider.getName(), endpoint, status, body, durationMs);

        } catch (HttpStatusCodeException ex) {
            // Erreur HTTP 4xx / 5xx — on a quand même un status et un body
            long durationMs = System.currentTimeMillis() - startMs;
            int status = ex.getStatusCode().value();
            String body = ex.getResponseBodyAsString();

            log.warn("Provider '{}' returned HTTP {}: {}", provider.getName(), status, body);

            persistProviderResponse(payload, status, body, false);
            persistExchangeLog(pipeline, status, body, durationMs, ex.getMessage());

            return ProviderDispatchResult.httpError(
                    provider.getId(), provider.getName(), endpoint, status, body, durationMs);

        } catch (ResourceAccessException ex) {
            // Timeout, DNS, connexion refusée
            long durationMs = System.currentTimeMillis() - startMs;
            String errorMsg = "Network error calling provider '" + provider.getName() + "': " + ex.getMessage();

            log.error(errorMsg);

            persistProviderResponse(payload, 0, null, false);
            persistExchangeLog(pipeline, 0, null, durationMs, errorMsg);

            return ProviderDispatchResult.networkError(
                    provider.getId(), provider.getName(), endpoint, durationMs, errorMsg);

        } catch (Exception ex) {
            // Toute autre erreur inattendue
            long durationMs = System.currentTimeMillis() - startMs;
            String errorMsg = "Unexpected error calling provider '" + provider.getName() + "': " + ex.getMessage();

            log.error(errorMsg, ex);

            persistProviderResponse(payload, 0, null, false);
            persistExchangeLog(pipeline, 0, null, durationMs, errorMsg);

            return ProviderDispatchResult.networkError(
                    provider.getId(), provider.getName(), endpoint, durationMs, errorMsg);
        }
    }

    // -------------------------------------------------------------------------
    // Persistence helpers
    // -------------------------------------------------------------------------

    /**
     * Persiste (ou met à jour) la {@link ProviderResponse} liée au payload.
     * Un payload n'a qu'une seule ProviderResponse (@OneToOne unique=true).
     * Si une réponse existe déjà (re-dispatch), on la met à jour.
     */
    private void persistProviderResponse(Payload payload, int httpStatus,
                                         String rawBody, boolean success) {
        ProviderResponse pr = providerResponseRepository
                .findByPayloadId(payload.getId())
                .orElseGet(() -> ProviderResponse.builder()
                        .payload(payload)
                        .build());

        pr.setHttpStatus(httpStatus);
        pr.setRawContent(rawBody != null ? rawBody : "");
        pr.setSuccess(success);
        pr.setReceivedAt(LocalDateTime.now());
        providerResponseRepository.save(pr);
    }

    /**
     * Persiste un {@link ExchangeLog} pour traçabilité complète de l'échange.
     */
    private void persistExchangeLog(Pipeline pipeline, int httpStatus,
                                    String message, long durationMs,
                                    String errorDetail) {
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

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    /**
     * Collecte les providers du pipeline.
     * Actuellement un pipeline est lié à un seul provider.
     * Cette méthode centralise la logique pour faciliter la migration
     * vers une relation @ManyToMany sans toucher au code appelant.
     */
    private List<Provider> collectProviders(Pipeline pipeline) {
        if (pipeline.getProvider() == null) {
            return List.of();
        }
        return List.of(pipeline.getProvider());
    }

    /**
     * Construit un {@link RestTemplate} avec le timeout (en secondes) du provider.
     * Chaque appel crée son propre RestTemplate pour isoler les timeouts.
     */
    private RestTemplate buildRestTemplate(int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds > 0 ? timeoutSeconds : 30);
        return restTemplateBuilder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
    }

    /**
     * Construit les headers HTTP.
     * Le Content-Type est déterminé par le {@code outputFormat} du pipeline.
     */
    private HttpHeaders buildHeaders(Pipeline pipeline) {
        HttpHeaders headers = new HttpHeaders();
        MediaType contentType = switch (pipeline.getOutputFormat()) {
            case XML  -> MediaType.APPLICATION_XML;
            case JSON -> MediaType.APPLICATION_JSON;
            default   -> MediaType.APPLICATION_JSON;
        };
        headers.setContentType(contentType);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.ALL));
        return headers;
    }

    /** Tronque une chaîne pour éviter de dépasser les limites de colonne TEXT. */
    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}