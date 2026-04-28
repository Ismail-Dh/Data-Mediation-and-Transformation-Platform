package com.miniESB.controller;

import com.miniESB.dto.Pipeline.CreatePipelineRequest;
import com.miniESB.dto.Pipeline.PipelineResponse;
import com.miniESB.dto.Pipeline.UpdatePipelineRequest;
import com.miniESB.service.PipelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Tag(name = "Pipelines", description = "Pipeline management")
@RestController
@RequestMapping("/api/pipelines")
public class PipelineController {

    private final PipelineService pipelineService;

    // --- Constructor ---
    @Autowired
    public PipelineController(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    // --- Create a new Pipeline ---
    @Operation(summary = "Create a pipeline", description = "Creates a new pipeline. DEVELOPER only.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Pipeline created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "404", description = "Provider not found")
    })
    @PostMapping
    @PreAuthorize("hasRole('DEVELOPER')") 
    public ResponseEntity<PipelineResponse> createPipeline(
            @Validated @RequestBody CreatePipelineRequest request,
            Principal principal) {
        return new ResponseEntity<>(pipelineService.createPipeline(request, principal.getName()), HttpStatus.CREATED);
    }

    // --- Update an existing Pipeline ---
    @Operation(summary = "Update a pipeline", description = "Partial update of a pipeline. Owner (DEVELOPER) only.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pipeline updated successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied - not the owner"),
        @ApiResponse(responseCode = "404", description = "Pipeline not found")
    })
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('DEVELOPER')") 
    public ResponseEntity<PipelineResponse> updatePipeline(
            @PathVariable Long id,
            @RequestBody UpdatePipelineRequest request,
            Principal principal) {
        return ResponseEntity.ok(pipelineService.updatePipeline(id, request, principal.getName()));
    }

    // --- Delete a Pipeline ---
    @Operation(summary = "Delete a pipeline", description = "Deletes a pipeline. Owner (DEVELOPER) only.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Pipeline deleted successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied - not the owner"),
        @ApiResponse(responseCode = "404", description = "Pipeline not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DEVELOPER')") 
    public ResponseEntity<Void> deletePipeline(
            @PathVariable Long id,
            Principal principal) {
        pipelineService.deletePipeline(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    // --- Get Pipeline by ID ---
    @Operation(summary = "Get pipeline by ID", description = "Returns a pipeline by ID. Accessible by DEVELOPER (owner) or ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pipeline returned successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Pipeline not found")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')") 
    public ResponseEntity<PipelineResponse> getPipelineById(
            @PathVariable Long id,
            Principal principal) {
        return ResponseEntity.ok(pipelineService.getPipelineById(id, principal.getName()));
    }

    // --- Get Pipelines owned by current user ---
    @Operation(summary = "Get my pipelines", description = "Returns all pipelines created by the authenticated user. DEVELOPER only.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List returned successfully")
    })
    @GetMapping("/my")
    @PreAuthorize("hasRole('DEVELOPER')") 
    public ResponseEntity<List<PipelineResponse>> getMyPipelines(Principal principal) {
        return ResponseEntity.ok(pipelineService.getMyPipelines(principal.getName()));
    }

    // --- Get all Pipelines (Admin only) ---
    @Operation(summary = "Get all pipelines", description = "Returns all pipelines. ADMIN only.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List returned successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PipelineResponse>> getAllPipelines() {
        return ResponseEntity.ok(pipelineService.getAllPipelines());
    }
}