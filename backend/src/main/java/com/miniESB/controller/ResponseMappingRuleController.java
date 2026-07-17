package com.miniESB.controller;

import com.miniESB.dto.response.CreateResponseMappingRuleRequest;
import com.miniESB.dto.response.ResponseMappingRuleResponse;
import com.miniESB.service.ResponseMappingRuleAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * T6 — CRUD des règles de validation/transformation des réponses providers.
 *
 * <pre>
 * GET    /api/pipelines/{pipelineId}/response-rules
 * POST   /api/pipelines/{pipelineId}/response-rules
 * PUT    /api/pipelines/{pipelineId}/response-rules/{ruleId}
 * DELETE /api/pipelines/{pipelineId}/response-rules/{ruleId}
 * </pre>
 */
@RestController
@RequestMapping("/api/pipelines/{pipelineId}/response-rules")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)

@CrossOrigin(origins = "*")
public class ResponseMappingRuleController {

    private final ResponseMappingRuleAdminService responseMappingRuleAdminService;

    @GetMapping
    public ResponseEntity<List<ResponseMappingRuleResponse>> getRules(
            @PathVariable Long pipelineId) {
        return ResponseEntity.ok(responseMappingRuleAdminService.getRules(pipelineId));
    }

    @PostMapping
    public ResponseEntity<ResponseMappingRuleResponse> createRule(
            @PathVariable Long pipelineId,
            @Valid @RequestBody CreateResponseMappingRuleRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(responseMappingRuleAdminService.createRule(pipelineId, request));
    }

    @PutMapping("/{ruleId}")
    public ResponseEntity<ResponseMappingRuleResponse> updateRule(
            @PathVariable Long pipelineId,
            @PathVariable Long ruleId,
            @Valid @RequestBody CreateResponseMappingRuleRequest request) {
        return ResponseEntity.ok(responseMappingRuleAdminService.updateRule(ruleId, request));
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteRule(
            @PathVariable Long pipelineId,
            @PathVariable Long ruleId) {
        responseMappingRuleAdminService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }
}