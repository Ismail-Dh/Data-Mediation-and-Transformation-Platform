package com.miniESB.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.exception.DockerBuildException;
import com.miniESB.exception.DockerDaemonException;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.service.impl.DockerImageGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contrôleur SSE qui streame les logs docker build en temps réel vers le frontend
 * Angular via EventSource.
 *
 * <h3>Endpoint principal</h3>
 * <pre>GET /api/pipelines/{pipelineId}/build-logs/stream</pre>
 *
 * <h3>Événements SSE émis</h3>
 * <table>
 *   <tr><th>Nom</th><th>Quand</th><th>Corps</th></tr>
 *   <tr><td>(sans nom)</td><td>Chaque ligne docker build</td><td>texte brut</td></tr>
 *   <tr><td>BUILD_COMPLETE</td><td>Build réussi</td><td>{type, imageId, size, duration}</td></tr>
 *   <tr><td>BUILD_FAILED</td><td>Toute erreur</td><td>{type, errorType, errorMessage, exitCode?, buildLog?}</td></tr>
 * </table>
 *
 * <h3>Codes {@code errorType} dans BUILD_FAILED</h3>
 * <ul>
 *   <li>{@code PIPELINE_NOT_FOUND}     — pipelineId inexistant</li>
 *   <li>{@code PIPELINE_NOT_VALIDATED} — pipeline pas en statut VALIDATED</li>
 *   <li>{@code DAEMON_UNREACHABLE}     — daemon Docker injoignable (503 logique)</li>
 *   <li>{@code BUILD_ERROR}            — {@code docker build} exit code != 0</li>
 *   <li>{@code BUILD_FAILED}           — erreur inattendue</li>
 * </ul>
 *
 * <h3>Garantie pipeline</h3>
 * Quelle que soit l'erreur, le statut du pipeline reste {@code VALIDATED}.
 * Seul le {@link com.miniESB.domain.entity.DockerImage} passe en {@code FAILED}.
 */
@Slf4j
@Tag(name = "Build Monitor", description = "SSE streaming of Docker build logs in real time")
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/api/pipelines/{pipelineId}/build-logs")
@PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
@RequiredArgsConstructor
public class BuildMonitorController {

    private final DockerImageGeneratorService generatorService;
    private final ObjectMapper objectMapper;

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /stream  — build SSE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Lance le build Docker et streame chaque ligne de log via SSE.
     *
     * <p>Consommation côté Angular :
     * <pre>{@code
     * const es = new EventSource('/api/pipelines/42/build-logs/stream');
     * es.onmessage = e => console.log(e.data);              // lignes de log brutes
     * es.addEventListener('BUILD_COMPLETE', e => {
     *   const d = JSON.parse(e.data);
     *   // d.imageId, d.size, d.duration
     * });
     * es.addEventListener('BUILD_FAILED', e => {
     *   const d = JSON.parse(e.data);
     *   // d.errorType  : 'DAEMON_UNREACHABLE' | 'BUILD_ERROR' | ...
     *   // d.errorMessage
     *   // d.exitCode   (si BUILD_ERROR)
     *   // d.buildLog   (si BUILD_ERROR, log complet)
     * });
     * }</pre>
     *
     * <p>Si le daemon est injoignable ou si le build échoue, un événement
     * {@code BUILD_FAILED} enrichi est envoyé et la connexion est fermée proprement.
     * Le pipeline reste {@code VALIDATED} dans tous les cas d'erreur.
     *
     * @param pipelineId identifiant du pipeline à builder
     * @return SseEmitter — Spring maintient la connexion jusqu'au {@code complete()} ou timeout
     */
    @Operation(
            summary = "Stream Docker build logs via Server-Sent Events",
            description = """
            Starts a Docker build and streams each log line in real time.

            **Error events** (name=BUILD_FAILED):
            - `DAEMON_UNREACHABLE` — Docker daemon is not running. Pipeline stays VALIDATED.
            - `BUILD_ERROR`        — docker build exited with non-zero code. Includes exitCode + buildLog. Pipeline stays VALIDATED.
            - `PIPELINE_NOT_FOUND`     — pipelineId does not exist.
            - `PIPELINE_NOT_VALIDATED` — pipeline is not in VALIDATED status.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "SSE stream opened — events will follow as text/event-stream"),
            @ApiResponse(responseCode = "404",
                    description = "Pipeline not found (emitted as BUILD_FAILED SSE event before stream closes)")
    })
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamBuildLogs(@PathVariable Long pipelineId) {

        // Timeout 0 = pas de timeout côté serveur (le build peut être long)
        SseEmitter emitter = new SseEmitter(0L);

        // ── Handlers de cycle de vie (appelables depuis n'importe quel thread) ──
        emitter.onTimeout(() -> {
            log.warn("SSE emitter timed out for pipeline={}", pipelineId);
            emitter.complete();
        });

        emitter.onError(ex -> {
            log.debug("SSE emitter error for pipeline={}: {}", pipelineId, ex.getMessage());
            emitter.complete();
        });

        emitter.onCompletion(() ->
                log.debug("SSE emitter completed for pipeline={}", pipelineId));

        // ── Guard : vérification synchrone avant de lancer le thread async ────
        // On vérifie ici ce qu'on peut vérifier sans démarrer le build.
        // Les erreurs Docker (daemon, build) sont gérées dans le service @Async.
        try {
            validatePipelineExistsAndIsReadyForBuild(pipelineId);
        } catch (ResourceNotFoundException e) {
            sendBuildFailedEvent(emitter, "PIPELINE_NOT_FOUND", e.getMessage(), null, null);
            return emitter;
        } catch (IllegalStateException e) {
            sendBuildFailedEvent(emitter, "PIPELINE_NOT_VALIDATED", e.getMessage(), null, null);
            return emitter;
        } catch (Exception e) {
            log.error("Unexpected error during pre-build validation for pipeline={}: {}",
                    pipelineId, e.getMessage());
            sendBuildFailedEvent(emitter, "BUILD_FAILED",
                    "Pre-build validation failed: " + e.getMessage(), null, null);
            return emitter;
        }

        // ── Lancement du build @Async ─────────────────────────────────────────
        // Le service gère en interne :
        //   - DockerDaemonException  → événement BUILD_FAILED errorType=DAEMON_UNREACHABLE
        //   - DockerBuildException   → événement BUILD_FAILED errorType=BUILD_ERROR + buildLog
        //   - Exception inattendue   → événement BUILD_FAILED errorType=BUILD_FAILED
        // Dans tous les cas le pipeline reste VALIDATED.
        generatorService.generateImageWithSse(pipelineId, emitter);

        return emitter;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /history  — historique des builds
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Retourne l'historique des builds d'un pipeline (du plus récent au plus ancien).
     *
     * <p>Utile pour rejouer les logs d'un build passé sans relancer le build.
     */
    @Operation(summary = "Get build history for a pipeline")
    @GetMapping("/history")
    public Object getBuildHistory(@PathVariable Long pipelineId) {
        return generatorService.getVersionHistory(pipelineId);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Private helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Vérifie de façon synchrone que le pipeline existe et est en statut {@code VALIDATED}.
     *
     * <p>Permet de rejeter immédiatement les requêtes invalides avant de lancer
     * le thread {@code @Async}, ce qui évite d'ouvrir une connexion SSE pour rien.
     *
     * @throws ResourceNotFoundException si le pipeline n'existe pas
     * @throws IllegalStateException     si le pipeline n'est pas en statut VALIDATED
     */
    private void validatePipelineExistsAndIsReadyForBuild(Long pipelineId) {
        generatorService.assertPipelineValidated(pipelineId);
    }

    /**
     * Émet un événement SSE {@code BUILD_FAILED} avec un corps JSON enrichi,
     * puis ferme proprement l'émetteur.
     *
     * <p>Format JSON émis :
     * <pre>{@code
     * {
     *   "type":         "BUILD_FAILED",
     *   "errorType":    "DAEMON_UNREACHABLE",
     *   "errorMessage": "Docker daemon is not reachable — ...",
     *   "exitCode":     1,           // seulement si BUILD_ERROR
     *   "buildLog":     "Step 1/..."  // seulement si BUILD_ERROR
     * }
     * }</pre>
     *
     * @param emitter   l'émetteur SSE à fermer après envoi
     * @param errorType code sémantique de l'erreur
     * @param message   message lisible par le développeur
     * @param exitCode  exit code docker (null si non applicable)
     * @param buildLog  log complet du build (null si non disponible)
     */
    void sendBuildFailedEvent(SseEmitter emitter,
                              String errorType,
                              String message,
                              Integer exitCode,
                              String buildLog) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type",         "BUILD_FAILED");
            payload.put("errorType",    errorType);
            payload.put("errorMessage", message != null ? message : "Unknown error");
            if (exitCode != null) {
                payload.put("exitCode", exitCode);
            }
            if (buildLog != null && !buildLog.isBlank()) {
                payload.put("buildLog", buildLog);
            }

            // Sérialisation déléguée à l'ObjectMapper injecté (Spring bean),
            // au lieu de reconstruire le JSON à la main (violation SRP/DIP corrigée).
            String json = objectMapper.writeValueAsString(payload);

            emitter.send(SseEmitter.event()
                    .name("BUILD_FAILED")
                    .data(json));
            emitter.complete();

        } catch (IOException ignored) {
            // Client déjà déconnecté — on ne peut rien faire de plus
            log.debug("Client disconnected before BUILD_FAILED event could be sent");
        }
    }
}