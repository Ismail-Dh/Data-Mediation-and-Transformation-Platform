package com.miniESB.controller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.miniESB.audit.Auditable;
import com.miniESB.dto.pipelineField.PipelineFieldRequest;
import com.miniESB.dto.pipelineField.PipelineFieldResponse;
import com.miniESB.service.PipelineFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Pipeline Fields", description = "Define the expected payload schema for a pipeline")
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/api/pipelines/{pipelineId}/fields")
@PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
@RequiredArgsConstructor
public class PipelineFieldController {

    private final PipelineFieldService pipelineFieldService;

    @Operation(summary = "Add a field to the pipeline schema")
    @PostMapping
    @Auditable(action = "CREATE", targetEntity = "Pipeline Field")
    public ResponseEntity<PipelineFieldResponse> addField(
            @PathVariable Long pipelineId,
            @Valid @RequestBody PipelineFieldRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pipelineFieldService.addField(pipelineId, request));
    }

    @Operation(summary = "Get all fields of a pipeline schema")
    @GetMapping
    @Auditable(action = "READ_ALL", targetEntity = "Pipeline Field")
    public ResponseEntity<List<PipelineFieldResponse>> getFields(@PathVariable Long pipelineId) {
        return ResponseEntity.ok(pipelineFieldService.getFields(pipelineId));
    }

    @Operation(summary = "Update a field")
    @PutMapping("/{fieldId}")
    @Auditable(action = "UPDATE", targetEntity = "Pipeline Field")
    public ResponseEntity<PipelineFieldResponse> updateField(
            @PathVariable Long pipelineId,
            @PathVariable Long fieldId,
            @Valid @RequestBody PipelineFieldRequest request) {
        return ResponseEntity.ok(pipelineFieldService.updateField(pipelineId, fieldId, request));
    }

    @Operation(summary = "Delete a field")
    @DeleteMapping("/{fieldId}")
    @Auditable(action = "DELETE", targetEntity = "Pipeline Field")
    public ResponseEntity<Void> deleteField(
            @PathVariable Long pipelineId,
            @PathVariable Long fieldId) {
        pipelineFieldService.deleteField(pipelineId, fieldId);
        return ResponseEntity.noContent().build();
    }
}