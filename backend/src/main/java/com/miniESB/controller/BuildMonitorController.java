package com.miniESB.controller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.miniESB.service.impl.DockerImageGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Contrôleur SSE qui streame les logs docker build en temps réel vers le frontend
 * Angular via EventSource. (Tâche 5.4)
 *
 * Endpoint : GET /api/pipelines/{pipelineId}/build-logs/stream
 */
@Tag(name = "Build Monitor", description = "SSE streaming of Docker build logs in real time")
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/api/pipelines/{pipelineId}/build-logs")
@PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
@RequiredArgsConstructor
public class BuildMonitorController {

    private final DockerImageGeneratorService generatorService;

    /**
     * Lance le build Docker et streame chaque ligne de log via SSE.
     *
     * <p>Le frontend Angular consomme ce flux avec :
     * <pre>
     *   const es = new EventSource('/api/pipelines/42/build-logs/stream');
     *   es.onmessage = e => console.log(e.data);           // lignes de log brutes
     *   es.addEventListener('BUILD_COMPLETE', e => { ... }); // imageId, size, duration
     *   es.addEventListener('BUILD_FAILED',   e => { ... }); // errorMessage
     * </pre>
     *
     * @param pipelineId identifiant du pipeline à builder
     * @return SseEmitter — Spring maintient la connexion ouverte jusqu'à completion
     */
    @Operation(summary = "Stream Docker build logs via Server-Sent Events")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamBuildLogs(@PathVariable Long pipelineId) {

        // Timeout 0 = pas de timeout côté serveur (le build peut être long)
        SseEmitter emitter = new SseEmitter(0L);

        // Gérer la déconnexion client AVANT de lancer le thread async
        // (les handlers sont thread-safe et appelables depuis n'importe quel thread)
        emitter.onTimeout(() -> emitter.complete());
        emitter.onError(ex -> emitter.complete());

        // Déléguer le build + streaming au service (@Async → thread dédié)
        generatorService.generateImageWithSse(pipelineId, emitter);

        return emitter;
    }
}