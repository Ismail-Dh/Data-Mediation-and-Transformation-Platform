package com.miniESB.controller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.miniESB.dto.mapping.ApplyMappingRequest;
import com.miniESB.dto.mapping.MappingResultResponse;
import com.miniESB.dto.mapping.MappingRuleRequest;
import com.miniESB.dto.mapping.MappingRuleResponse;
import com.miniESB.service.MappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Mapping Rules", description = "Define and apply field mapping rules for a pipeline")
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/api/pipelines/{pipelineId}/mappings")
@PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
@RequiredArgsConstructor
public class MappingRuleController {

    private final MappingService mappingService;

    @Operation(summary = "Create a mapping rule for a pipeline")
    @PostMapping
    public ResponseEntity<MappingRuleResponse> createRule(
            @PathVariable Long pipelineId,
            @Valid @RequestBody MappingRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mappingService.createRule(pipelineId, request));
    }

    @Operation(summary = "Get active mapping rules of a pipeline")
    @GetMapping
    public ResponseEntity<List<MappingRuleResponse>> getRules(@PathVariable Long pipelineId) {
        return ResponseEntity.ok(mappingService.getRulesByPipeline(pipelineId));
    }

    @Operation(summary = "Get all mapping rules of a pipeline (active and inactive)")
    @GetMapping("/all")
    public ResponseEntity<List<MappingRuleResponse>> getAllRules(@PathVariable Long pipelineId) {
        return ResponseEntity.ok(mappingService.getAllRulesByPipeline(pipelineId));
    }

    @Operation(summary = "Disable a mapping rule (soft delete)")
    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long pipelineId,
                                           @PathVariable Long ruleId) {
        mappingService.deleteRule(pipelineId, ruleId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Re-activate a disabled mapping rule")
    @PatchMapping("/{ruleId}/activate")
    public ResponseEntity<MappingRuleResponse> activateRule(@PathVariable Long pipelineId,
                                                             @PathVariable Long ruleId) {
        return ResponseEntity.ok(mappingService.activateRule(pipelineId, ruleId));
    }

    @Operation(summary = "Update an existing mapping rule (type and/or expression)")
    @PutMapping("/{ruleId}")
    public ResponseEntity<MappingRuleResponse> updateRule(
            @PathVariable Long pipelineId,
            @PathVariable Long ruleId,
            @Valid @RequestBody MappingRuleRequest request) {
        return ResponseEntity.ok(mappingService.updateRule(pipelineId, ruleId, request));
    }


    @Operation(summary = "Apply mapping rules to a raw JSON payload")
    @PostMapping("/apply")
    public ResponseEntity<MappingResultResponse> applyMapping(
            @PathVariable Long pipelineId,
            @Valid @RequestBody ApplyMappingRequest request) {
        return ResponseEntity.ok(
                mappingService.applyMappingToPayload(pipelineId, request.rawContent())
        );
    }
}