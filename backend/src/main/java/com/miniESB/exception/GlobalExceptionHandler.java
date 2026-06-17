package com.miniESB.exception;

import com.miniESB.service.impl.DockerImageGeneratorService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    // ── 400 Bean Validation (@Valid sur les DTOs) ─────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    // ── 400 Arguments illégaux (doublons fieldPath, enum invalide…) ──────────
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        return new ResponseEntity<>(Map.of("error", ex.getMessage()), HttpStatus.BAD_REQUEST);
    }

    // ── 403 Accès refusé ──────────────────────────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
        return new ResponseEntity<>(Map.of("error", ex.getMessage()), HttpStatus.FORBIDDEN);
    }

    // ── 404 Ressource introuvable ─────────────────────────────────────────────
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(ResourceNotFoundException ex) {
        return new ResponseEntity<>(Map.of("error", ex.getMessage()), HttpStatus.NOT_FOUND);
    }

    // ── 409 Conflit DB (unicité) ──────────────────────────────────────────────
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleConflict(DataIntegrityViolationException ex) {
        return new ResponseEntity<>(Map.of("error", ex.getMessage()), HttpStatus.CONFLICT);
    }

    // ── 409 Règle globale encore utilisée par un pipeline ────────────────────
    @ExceptionHandler(RuleInUseException.class)
    public ResponseEntity<Object> handleRuleInUse(RuleInUseException ex) {
        return new ResponseEntity<>(Map.of("error", ex.getMessage()), HttpStatus.CONFLICT);
    }

    // ── 422 Validation structurelle du payload (niveau 1) ────────────────────
    /**
     * Retourne un corps détaillé :
     * {
     *   "error":      "Payload structural validation failed",
     *   "timestamp":  "2025-05-13T10:42:00Z",
     *   "violationCount": 2,
     *   "violations": [
     *     { "fieldPath": "customer.email", "errorType": "MISSING_FIELD",  "message": "…" },
     *     { "fieldPath": "amount",         "errorType": "TYPE_MISMATCH",  "message": "…" }
     *   ]
     * }
     */
    @ExceptionHandler(PayloadValidationException.class)
    public ResponseEntity<Object> handlePayloadValidation(PayloadValidationException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error",          "Payload structural validation failed");
        body.put("timestamp",      Instant.now().toString());
        body.put("violationCount", ex.getViolations().size());
        body.put("violations",     ex.getViolations());
        return new ResponseEntity<>(body, HttpStatus.UNPROCESSABLE_ENTITY); // 422
    }

    // ── 503 Docker daemon injoignable ─────────────────────────────────────────
    /**
     * Levé par {@link DockerImageGeneratorService} quand {@code docker info} échoue
     * ou que le socket Docker est absent.
     *
     * <p>Le pipeline reste {@code VALIDATED}. Corps de réponse :
     * <pre>
     * {
     *   "error":           "Docker daemon is not reachable",
     *   "errorType":       "DAEMON_UNREACHABLE",
     *   "technicalDetail": "Cannot connect to the Docker daemon at unix:///var/run/docker.sock",
     *   "timestamp":       "2025-06-15T10:00:00Z",
     *   "hint":            "Ensure the Docker daemon is running and the socket is accessible."
     * }
     * </pre>
     */
    @ExceptionHandler(DockerDaemonException.class)
    public ResponseEntity<Object> handleDockerDaemon(DockerDaemonException ex,
                                                     HttpServletRequest request) {
        if (isSseRequest(request)) {
            return sseErrorResponse("DAEMON_UNREACHABLE",
                    ex.getMessage() + (ex.getTechnicalDetail() != null
                            ? " — " + ex.getTechnicalDetail() : ""),
                    null, null);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error",           "Docker daemon is not reachable");
        body.put("errorType",       "DAEMON_UNREACHABLE");
        if (ex.getTechnicalDetail() != null) {
            body.put("technicalDetail", ex.getTechnicalDetail());
        }
        body.put("timestamp", Instant.now().toString());
        body.put("hint",      "Ensure the Docker daemon is running and the socket is accessible.");
        return new ResponseEntity<>(body, HttpStatus.SERVICE_UNAVAILABLE); // 503
    }

    // ── 422 Build Docker échoué (exit code != 0) ──────────────────────────────
    /**
     * Levé par {@link DockerImageGeneratorService} quand {@code docker build} termine
     * avec un exit code non nul.
     *
     * <p>Le pipeline reste {@code VALIDATED}. Corps de réponse :
     * <pre>
     * {
     *   "error":     "Docker build failed",
     *   "errorType": "BUILD_ERROR",
     *   "exitCode":  1,
     *   "buildLog":  "Step 1/3 : FROM mini-esb-backend:latest\n...\nERROR: ...",
     *   "timestamp": "2025-06-15T10:00:00Z"
     * }
     * </pre>
     */
    @ExceptionHandler(DockerBuildException.class)
    public ResponseEntity<Object> handleDockerBuild(DockerBuildException ex,
                                                    HttpServletRequest request) {
        if (isSseRequest(request)) {
            return sseErrorResponse("BUILD_ERROR", ex.getMessage(),
                    ex.getExitCode(), ex.getBuildLog());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error",     "Docker build failed");
        body.put("errorType", "BUILD_ERROR");
        body.put("exitCode",  ex.getExitCode());
        if (ex.getBuildLog() != null && !ex.getBuildLog().isBlank()) {
            body.put("buildLog", ex.getBuildLog());
        }
        body.put("timestamp", Instant.now().toString());
        return new ResponseEntity<>(body, HttpStatus.UNPROCESSABLE_ENTITY); // 422
    }

    // ── 500 Fallback ──────────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAll(Exception ex, WebRequest request) {
        return new ResponseEntity<>(Map.of("error", "Internal server error"),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Détecte si la requête est une connexion SSE (Accept: text/event-stream). */
    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    /**
     * Répond avec un SseEmitter qui émet immédiatement un événement {@code BUILD_FAILED}
     * enrichi puis se ferme — compatible text/event-stream.
     *
     * @param errorType code sémantique de l'erreur
     * @param message   message lisible
     * @param exitCode  exit code docker (null si non applicable)
     * @param buildLog  log complet du build (null si non disponible)
     */
    private ResponseEntity<Object> sseErrorResponse(String errorType,
                                                    String message,
                                                    Integer exitCode,
                                                    String buildLog) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type",         "BUILD_FAILED");
            payload.put("errorType",    errorType);
            payload.put("errorMessage", message != null ? message : "Internal server error");
            if (exitCode != null) {
                payload.put("exitCode", exitCode);
            }
            if (buildLog != null && !buildLog.isBlank()) {
                payload.put("buildLog", buildLog);
            }

            // Sérialisation manuelle légère (pas d'ObjectMapper injecté ici)
            StringBuilder json = new StringBuilder("{");
            payload.forEach((k, v) -> {
                if (json.length() > 1) json.append(",");
                json.append("\"").append(k).append("\":");
                if (v instanceof Number) {
                    json.append(v);
                } else {
                    // Échapper les guillemets et sauts de ligne dans la valeur
                    String safe = v.toString()
                            .replace("\\", "\\\\")
                            .replace("\"", "'")
                            .replace("\n", "\\n")
                            .replace("\r", "");
                    json.append("\"").append(safe).append("\"");
                }
            });
            json.append("}");

            emitter.send(SseEmitter.event()
                    .name("BUILD_FAILED")
                    .data(json.toString()));
            emitter.complete();
        } catch (IOException ignored) { }

        return ResponseEntity
                .ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }
}