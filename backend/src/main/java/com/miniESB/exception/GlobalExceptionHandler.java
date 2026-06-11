package com.miniESB.exception;

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

    // ── 500 Fallback ──────────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAll(Exception ex, WebRequest request) {
        return new ResponseEntity<>(Map.of("error", "Internal server error"), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Détecte si la requête est une connexion SSE (Accept: text/event-stream). */
    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    /**
     * Répond avec un SseEmitter qui émet immédiatement un événement BUILD_FAILED
     * puis se ferme — compatible text/event-stream.
     */
    private ResponseEntity<Object> sseErrorResponse(String message) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            String safeMsg = (message != null ? message : "Internal server error")
                    .replace("\"", "'");
            emitter.send(SseEmitter.event()
                    .name("BUILD_FAILED")
                    .data("{\"type\":\"BUILD_FAILED\",\"errorMessage\":\"" + safeMsg + "\"}"));
            emitter.complete();
        } catch (IOException ignored) { }

        return ResponseEntity
                .ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }
}