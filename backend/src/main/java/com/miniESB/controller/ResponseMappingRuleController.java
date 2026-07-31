package com.miniESB.controller;

import com.miniESB.dto.response.CreateResponseMappingRuleRequest;
import com.miniESB.dto.response.ResponseMappingRuleResponse;
import com.miniESB.service.ResponseMappingRuleAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Response Mapping Rules", description = "CRUD for provider response validation/transformation rules (T6)")
@CrossOrigin(origins = "*")
public class ResponseMappingRuleController {

    private final ResponseMappingRuleAdminService responseMappingRuleAdminService;

    @Operation(summary = "Get all response mapping rules for a pipeline")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rules retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Pipeline not found")
    })
    @GetMapping
    public ResponseEntity<List<ResponseMappingRuleResponse>> getRules(
            @Parameter(description = "ID of the pipeline") @PathVariable Long pipelineId) {
        return ResponseEntity.ok(responseMappingRuleAdminService.getRules(pipelineId));
    }

    @Operation(summary = "Create a new response mapping rule for a pipeline")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Rule created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid rule payload"),
            @ApiResponse(responseCode = "404", description = "Pipeline not found")
    })
    @PostMapping
    public ResponseEntity<ResponseMappingRuleResponse> createRule(
            @Parameter(description = "ID of the pipeline") @PathVariable Long pipelineId,
            @Valid @RequestBody CreateResponseMappingRuleRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(responseMappingRuleAdminService.createRule(pipelineId, request));
    }

    @Operation(summary = "Update an existing response mapping rule")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rule updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid rule payload"),
            @ApiResponse(responseCode = "404", description = "Pipeline or rule not found")
    })
    @PutMapping("/{ruleId}")
    public ResponseEntity<ResponseMappingRuleResponse> updateRule(
            @Parameter(description = "ID of the pipeline") @PathVariable Long pipelineId,
            @Parameter(description = "ID of the rule to update") @PathVariable Long ruleId,
            @Valid @RequestBody CreateResponseMappingRuleRequest request) {
        return ResponseEntity.ok(responseMappingRuleAdminService.updateRule(ruleId, request));
    }

    @Operation(summary = "Delete a response mapping rule")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Rule deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Pipeline or rule not found")
    })
    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteRule(
            @Parameter(description = "ID of the pipeline") @PathVariable Long pipelineId,
            @Parameter(description = "ID of the rule to delete") @PathVariable Long ruleId) {
        responseMappingRuleAdminService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }
}