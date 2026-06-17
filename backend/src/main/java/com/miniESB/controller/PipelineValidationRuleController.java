package com.miniESB.controller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.miniESB.dto.pipelineValidationRule.PipelineValidationRuleRequest;
import com.miniESB.dto.pipelineValidationRule.PipelineValidationRuleResponse;
import com.miniESB.service.PipelineValidationRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Developer-facing CRUD for pipeline-scoped validation rules (niveau 2).
 *
 * Base path: /api/pipelines/{pipelineId}/validation-rules
 *
 * Two modes for POST:
 *   - { globalRuleId: 5 }                 → attaches an existing Admin rule
 *   - { fieldName, ruleType, [pattern] }  → creates a private rule
 */
@Tag(name = "Pipeline Validation Rules",
        description = "Manage niveau-2 validation rules for a pipeline (attach global or create private)")
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/api/pipelines/{pipelineId}/validation-rules")
@PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
@RequiredArgsConstructor
public class PipelineValidationRuleController {

    private final PipelineValidationRuleService pipelineValidationRuleService;

    // ── POST — attach global OR create private ────────────────────────────────
    @Operation(summary = "Attach a global rule or create a private validation rule",
            description = "If globalRuleId is provided, copies the global rule definition onto the pipeline. Otherwise creates a custom rule with the supplied fieldName + ruleType [+ pattern].")
    @PostMapping
    public ResponseEntity<PipelineValidationRuleResponse> addRule(
            @PathVariable Long pipelineId,
            @Valid @RequestBody PipelineValidationRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pipelineValidationRuleService.addRule(pipelineId, request));
    }

    // ── GET all ───────────────────────────────────────────────────────────────
    @Operation(summary = "List all validation rules of a pipeline (active + inactive)")
    @GetMapping
    public ResponseEntity<List<PipelineValidationRuleResponse>> getRules(
            @PathVariable Long pipelineId) {
        return ResponseEntity.ok(pipelineValidationRuleService.getRules(pipelineId));
    }

    // ── PUT — update private rule ─────────────────────────────────────────────
    @Operation(summary = "Update a private validation rule",
            description = "Only private (non-global) rules can be edited. Global rules must be detached and re-created.")
    @PutMapping("/{ruleId}")
    public ResponseEntity<PipelineValidationRuleResponse> updateRule(
            @PathVariable Long pipelineId,
            @PathVariable Long ruleId,
            @Valid @RequestBody PipelineValidationRuleRequest request) {
        return ResponseEntity.ok(
                pipelineValidationRuleService.updateRule(pipelineId, ruleId, request));
    }

    // ── PATCH — toggle active ─────────────────────────────────────────────────
    @Operation(summary = "Toggle active/inactive on a validation rule")
    @PatchMapping("/{ruleId}/toggle")
    public ResponseEntity<PipelineValidationRuleResponse> toggleActive(
            @PathVariable Long pipelineId,
            @PathVariable Long ruleId) {
        return ResponseEntity.ok(
                pipelineValidationRuleService.toggleActive(pipelineId, ruleId));
    }

    // ── DELETE ────────────────────────────────────────────────────────────────
    @Operation(summary = "Remove a validation rule from the pipeline",
            description = "Detaches a global rule or permanently deletes a private rule.")
    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteRule(
            @PathVariable Long pipelineId,
            @PathVariable Long ruleId) {
        pipelineValidationRuleService.deleteRule(pipelineId, ruleId);
        return ResponseEntity.noContent().build();
    }
}