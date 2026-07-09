package com.miniESB.controller;

import com.miniESB.dto.process.ProcessRequest;
import com.miniESB.dto.process.ProcessResponse;
import com.miniESB.service.ProcessOrchestrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * T7 — Point d'entrée unique du flux complet miniESB.
 *
 * <pre>
 * POST /api/process
 * {
 *   "pipelineId": 1,
 *   "rawContent": "{\"amount\": 250}",
 *   "inputFormat": "JSON"
 * }
 * </pre>
 *
 * Retourne le résultat agrégé avec le détail par provider.
 */
@RestController
@RequestMapping("/api/process")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@CrossOrigin(origins = "*")
public class ProcessController {

    private final ProcessOrchestrationService orchestrationService;

    /**
     * Déclenche le flux complet :
     * validation → mapping → dispatch → validation réponses → agrégation.
     */
    @PostMapping
    public ResponseEntity<ProcessResponse> process(
            @Valid @RequestBody ProcessRequest request) {
        ProcessResponse response = orchestrationService.process(request);
        return ResponseEntity.ok(response);
    }
}